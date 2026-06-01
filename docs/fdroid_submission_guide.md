# F-Droid Submission Guide for Enclave

This guide outlines the step-by-step process, prerequisites, and metadata configuration required to submit Enclave (`dev.saifmukhtar.enclave`) to the official F-Droid repository. 

Our Android client is engineered as a FOSS-compliant (Free and Open Source Software) monorepo module. It dynamically compiles its native `libsignal` cryptography dependency from a tracked Git submodule during the build pipeline, ensuring F-Droid acceptance with zero proprietary blobs.

---

## 1. Prerequisites Checklist

Before submitting, ensure you have:
- [x] **Public Git Repository:** Source code pushed to [github.com/saifmukhtar/enclave](https://github.com/saifmukhtar/enclave).
- [x] **Stable Release Tag:** Git release tag created and pushed (e.g. `v2.0.0` at versionCode `3`).
- [x] **Submodule Configuration:** `libsignal-src` configured at tag `v0.39.2` under the root tree directory.
- [x] **GitLab Account:** Required to fork the official metadata repository and open your merge request.

---

## 2. App & Build Specification Details

* **Application ID:** `dev.saifmukhtar.enclave`
* **Subdirectory Path:** `apps/android`
* **Submodules Included:** Yes (`libsignal-src` tracked at tag `v0.39.2`)
* **Gradle Target Property:** `-Pfdroid=true` (Used to trigger FOSS-only dynamic AAR library imports)
* **Minimum SDK:** API 34 (Android 14)
* **License:** AGPL-3.0-only

---

## 3. The Active F-Droid Metadata File

Create a YAML file named exactly **`dev.saifmukhtar.enclave.yml`** inside the `metadata/` directory of F-Droid's repository. Below is our verified build recipe configured specifically for Enclave:

```yaml
Categories:
  - Internet
  - Security
License: AGPL-3.0-only
SourceCode: https://github.com/saifmukhtar/enclave
IssueTracker: https://github.com/saifmukhtar/enclave/issues
WebSite: https://github.com/saifmukhtar/enclave

RepoType: git
Repo: https://github.com/saifmukhtar/enclave.git

Builds:
  - versionName: 2.0.0
    versionCode: 3
    commit: v2.0.0
    subdir: apps/android
    submodules: yes
    prebuild: |
      # Save the absolute path of Enclave's Android directory
      ENCLAVE_DIR=$(pwd)
      
      # Navigate to the submodule java client and build native libraries
      cd ../../libsignal-src/java
      echo "sdk.dir=$ANDROID_HOME" > local.properties
      ./gradlew assembleRelease -x test
      
      # Return to Enclave Android directory and copy compile artifacts
      cd $ENCLAVE_DIR
      mkdir -p app/libs
      cp ../../libsignal-src/java/android/build/outputs/aar/*-release.aar app/libs/
      cp ../../libsignal-src/java/client/build/libs/*.jar app/libs/
    gradle:
      - yes
    gradleprops:
      - fdroid=true

AutoUpdateMode: Version
UpdateCheckMode: Tags
```

---

## 4. Step-by-Step Submission Procedure

Follow these sequential steps to submit the metadata file to F-Droid:

### Step 1: Fork the F-Droid Data Repository
1. Go to the official [fdroiddata GitLab repository](https://gitlab.com/fdroid/fdroiddata).
2. Click the **Fork** button to create a copy under your personal GitLab account namespace.
3. Clone your fork locally to your machine:
   ```bash
   git clone https://gitlab.com/your-username/fdroiddata.git
   cd fdroiddata
   ```

### Step 2: Add Enclave's Build Recipe
1. Create a dedicated branch for your MR submission:
   ```bash
   git checkout -b add-enclave
   ```
2. Copy the active YAML block from Section 3 and write it to `metadata/dev.saifmukhtar.enclave.yml`.
3. Commit the new recipe file and push to your fork:
   ```bash
   git add metadata/dev.saifmukhtar.enclave.yml
   git commit -m "Add dev.saifmukhtar.enclave at v2.0.0"
   git push origin add-enclave
   ```

### Step 3: Open the Merge Request (MR)
1. Navigate to your fork page on GitLab.
2. GitLab will present a banner: **"Create merge request"**. Click it.
3. Fill out the Merge Request template:
   * Confirm that the app has **no proprietary dependencies** (checked: all JNI/Rust dependencies compile from source).
   * Confirm there are **no anti-features** (checked: zero ads, zero tracking analytics, and no third-party cloud pushes).
4. Submit the Merge Request.

F-Droid's automated GitLab CI build runner will automatically launch a secure virtual container, fetch your repository, execute the custom `prebuild` submodule compilation routine, and compile the final FOSS-compliant APK for testing.

---

## 5. Local Sandbox Verification (Highly Recommended)

Before opening the Merge Request, you can run F-Droid's official build servers in a containerized Docker sandbox on your local machine to verify the build completes successfully:

1. **Install F-Droid Server Utilities:**
   ```bash
   sudo apt update
   sudo apt install fdroidserver
   ```
2. **Lint check your metadata file:**
   Inside your cloned `fdroiddata` folder, run:
   ```bash
   fdroid readmeta
   fdroid lint dev.saifmukhtar.enclave
   ```
3. **Execute Sandbox Containerized Build:**
   Compile the app inside F-Droid's official build environment to guarantee server success:
   ```bash
   fdroid build -v -s dev.saifmukhtar.enclave
   ```
   *Verify that the output finishes with `BUILD SUCCESSFUL` and packages the dynamically generated `libsignal` binaries properly.*
