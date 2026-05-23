package com.enclave.app.ui.kiss.physics

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.*

@Serializable
data class RawNode(val id: Int, val x: Float, val y: Float)

@Serializable
data class RawEdge(val v1: Int, val v2: Int)

@Serializable
data class LipMeshDto(val vertices: List<RawNode>, val edges: List<RawEdge>)

data class PhysicsNode(
    val id: Int,
    var currentX: Float,
    var currentY: Float,
    val baseX: Float,
    val baseY: Float,
    var velocityX: Float = 0f,
    var velocityY: Float = 0f,
    var forceX: Float = 0f,
    var forceY: Float = 0f
)

data class Spring(
    val node1: PhysicsNode,
    val node2: PhysicsNode,
    val restingLength: Float
)

class LipPhysicsEngine(context: Context, rawAssetId: Int) {
    var nodes = listOf<PhysicsNode>()
    var springs = listOf<Spring>()
    
    // Real-time engine telemetry exposed for the Haptic & Audio Engines
    var engineKineticEnergy: Float = 0f
        private set
    var engineMeshStress: Float = 0f
        private set

    // Hyperparameters
    private val kAnchor = 0.12f
    private val kSpring = 0.45f
    private val kRepel = 1.35f
    private val kGravity = 0.028f
    private val muDamping = 0.80f

    init {
        try {
            val jsonString = context.resources.openRawResource(rawAssetId)
                .bufferedReader().use { it.readText() }
            val dto = Json.decodeFromString<LipMeshDto>(jsonString)
            initializeMesh(dto)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initializeMesh(dto: LipMeshDto) {
        if (dto.vertices.isEmpty()) return

        // 1. Compute Centroid
        val sumX = dto.vertices.sumOf { it.x.toDouble() }.toFloat()
        val sumY = dto.vertices.sumOf { it.y.toDouble() }.toFloat()
        val centroidX = sumX / dto.vertices.size
        val centroidY = sumY / dto.vertices.size

        // 2. Calibrate and Localize Nodes
        val nodeMap = mutableMapOf<Int, PhysicsNode>()
        nodes = dto.vertices.map { raw ->
            val localizedX = raw.x - centroidX
            val localizedY = raw.y - centroidY
            val node = PhysicsNode(
                id = raw.id,
                currentX = localizedX,
                currentY = localizedY, // Correctly initialized to localizedY
                baseX = localizedX,
                baseY = localizedY
            )
            nodeMap[raw.id] = node
            node
        }

        // 3. Build Springs using initial Euclidean distances
        springs = dto.edges.mapNotNull { edge ->
            val n1 = nodeMap[edge.v1]
            val n2 = nodeMap[edge.v2]
            if (n1 != null && n2 != null) {
                val dx = n1.baseX - n2.baseX
                val dy = n1.baseY - n2.baseY
                val length = sqrt(dx * dx + dy * dy)
                Spring(n1, n2, length)
            } else null
        }
    }

    fun updatePhysics(
        activeTouchX: Float?, 
        activeTouchY: Float?, 
        touchMajor: Float, 
        touchMinor: Float, 
        orientationRad: Float, 
        pressure: Float, 
        canvasWidth: Float,
        gravityX: Float,
        gravityY: Float
    ) {
        if (nodes.isEmpty()) return

        // Sanitize inputs
        val safeGravityX = if (gravityX.isNaN() || gravityX.isInfinite()) 0f else gravityX
        val safeGravityY = if (gravityY.isNaN() || gravityY.isInfinite()) 0f else gravityY
        val safePressure = if (pressure.isNaN() || pressure.isInfinite() || pressure < 0f) 1.0f else pressure
        val safeTouchMajor = if (touchMajor.isNaN() || touchMajor.isInfinite()) 0f else touchMajor
        val safeTouchMinor = if (touchMinor.isNaN() || touchMinor.isInfinite()) 0f else touchMinor
        val safeOrientation = if (orientationRad.isNaN() || orientationRad.isInfinite()) 0f else orientationRad

        val touchX = if (activeTouchX == null || activeTouchX.isNaN()) null else activeTouchX
        val touchY = if (activeTouchY == null || activeTouchY.isNaN()) null else activeTouchY

        // Reset forces for this frame tick
        for (node in nodes) {
            node.forceX = 0f
            node.forceY = 0f
        }

        // 1. Apply Base Anchor Forces (Hooke's Law)
        for (node in nodes) {
            node.forceX += -kAnchor * (node.currentX - node.baseX)
            node.forceY += -kAnchor * (node.currentY - node.baseY)
        }

        // 2. Apply Mutual Spring Forces
        var totalStress = 0f
        for (spring in springs) {
            val dx = spring.node2.currentX - spring.node1.currentX
            val dy = spring.node2.currentY - spring.node1.currentY
            val currentDistance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            
            val deltaX = currentDistance - spring.restingLength
            totalStress += abs(deltaX)

            // Unit direction vector
            val ux = dx / currentDistance
            val uy = dy / currentDistance

            // Hooke's Law force vector
            val fx = kSpring * deltaX * ux
            val fy = kSpring * deltaX * uy

            spring.node1.forceX += fx
            spring.node1.forceY += fy
            spring.node2.forceX -= fx
            spring.node2.forceY -= fy
        }
        engineMeshStress = if (springs.isNotEmpty()) totalStress / springs.size else 0f

        // 3. Apply Active Touch Repulsion Force (Ellipse footprint conversion)
        if (touchX != null && touchY != null && safeTouchMajor > 0f) {
            val drawingScale = (canvasWidth * 0.6f) / 4.5f
            val rMajor = (safeTouchMajor / (2f * drawingScale)).coerceAtLeast(0.1f)
            val rMinor = (safeTouchMinor / (2f * drawingScale)).coerceAtLeast(0.1f)

            val cosTheta = cos(-safeOrientation)
            val sinTheta = sin(-safeOrientation)

            for (node in nodes) {
                // Vector relative to local touch space origin
                val rNodeX = node.currentX - touchX
                val rNodeY = node.currentY - touchY

                // Rotate node coordinates into ellipse-aligned space
                val xRot = rNodeX * cosTheta - rNodeY * sinTheta
                val yRot = rNodeX * sinTheta + rNodeY * cosTheta

                // Ellipse containment calculation
                val dEllipse = sqrt((xRot / rMajor).pow(2) + (yRot / rMinor).pow(2))

                if (dEllipse < 1.0f) {
                    val nodeMag = sqrt(node.currentX * node.currentX + node.currentY * node.currentY).coerceAtLeast(0.001f)
                    val repelFactor = kRepel * safePressure * (1.0f - dEllipse)

                    // Push outward from the center grid
                    node.forceX += repelFactor * (node.currentX / nodeMag)
                    node.forceY += repelFactor * (node.currentY / nodeMag)
                }
            }
        }

        // 4. Apply Accelerometer Gravity Sag Forces
        for (node in nodes) {
            node.forceX += safeGravityX * kGravity
            node.forceY += safeGravityY * kGravity
        }

        // 5. Verlet Integration / Velocity Update with Damping
        var totalKineticEnergy = 0f
        for (node in nodes) {
            node.velocityX = (node.velocityX + node.forceX) * muDamping
            node.velocityY = (node.velocityY + node.forceY) * muDamping
            node.currentX += node.velocityX
            node.currentY += node.velocityY

            if (node.currentX.isNaN() || node.currentY.isNaN() || node.velocityX.isNaN() || node.velocityY.isNaN()) {
                node.currentX = node.baseX
                node.currentY = node.baseY
                node.velocityX = 0f
                node.velocityY = 0f
            }

            totalKineticEnergy += (node.velocityX * node.velocityX + node.velocityY * node.velocityY)
        }
        engineKineticEnergy = if (nodes.isNotEmpty()) totalKineticEnergy / nodes.size else 0f
    }
}
