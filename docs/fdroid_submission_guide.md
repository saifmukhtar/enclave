# F-Droid Submission Guide for Enclave

This guide outlines the step-by-step process, prerequisites, and metadata configuration required to submit Enclave (`dev.saifmukhtar.enclave`) to the official F-Droid repository. 

Our Android client is engineered as a FOSS-compliant (Free and Open Source Software) monorepo module. It dynamically compiles its native `libsignal` cryptography dependency from a tracked Git submodule during the build pipeline, ensuring F-Droid acceptance with zero proprietary blobs.

---

## 1. Prerequisites Checklist

Before submitting, ensure you have:
- [x] **Public Git Repository:** Source code pushed to [github.com/saifmukhtar/enclave](https://github.com/saifmukhtar/enclave).
- [x] **Stable Release Tag:** Git release tag created and pushed (e.g. `v3.0.0` at versionCode `4`).
- [x] **Submodule Configuration:** `libsignal-src` configured at tag `v0.39.2` under the root tree directory, with Rust dependencies vendored and committed.
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
  - versionName: '2.0.0'
    versionCode: 3
    commit: v2.0.0
    subdir: apps/android
    submodules: true
    ndk: r25c
    prebuild: |
      # Save the absolute path of Enclave's Android directory
      ENCLAVE_DIR=$(pwd)
      
      # From apps/android, the submodule lives two directories up
      cd ../../libsignal-src/java
      echo "sdk.dir=$ANDROID_HOME" > local.properties
      echo "ndk.dir=$$NDK$$" >> local.properties
      ./gradlew assembleRelease -x test
      
      # Return to Enclave Android directory and copy artifacts safely
      cd $ENCLAVE_DIR
      mkdir -p app/libs
      cp ../../libsignal-src/java/android/build/outputs/aar/*-release.aar app/libs/
      cp ../../libsignal-src/java/client/build/libs/*.jar app/libs/
    gradle:
      - yes
    gradleprops:
      - fdroid=true

  - versionName: '3.0.0'
    versionCode: 4
    commit: v3.0.0
    subdir: apps/android
    submodules: true
    ndk: r25c
    prebuild: |
      # Save the absolute path of Enclave's Android directory
      ENCLAVE_DIR=$(pwd)
      
      # From apps/android, the submodule lives two directories up
      cd ../../libsignal-src/java
      echo "sdk.dir=$ANDROID_HOME" > local.properties
      echo "ndk.dir=$$NDK$$" >> local.properties
      ./gradlew assembleRelease -x test
      
      # Return to Enclave Android directory and copy artifacts safely
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
CurrentVersion: '3.0.0'
CurrentVersionCode: 4
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

---

## 6. Offline Rust Dependency Compilation (Cargo Vendoring)

Because F-Droid build servers compile applications in an isolated sandbox with **no internet access**, standard Rust Cargo compilations will fail when cargo attempts to fetch library dependencies from crates.io.

To resolve this, we vendor our Rust dependencies inside the `libsignal-src` submodule and commit them. Before tagging a new release, developers must perform the following steps:

1. **Vendor Cargo Dependencies:**
   Navigate to the root of the `libsignal-src` directory and run:
   ```bash
   cargo vendor
   ```
   This downloads and structures all Rust crate dependencies into a local `vendor/` directory.

2. **Configure Cargo Offline Source Replacement:**
   Create or edit the local cargo configuration file at `libsignal-src/.cargo/config.toml` to tell Cargo to use the local `vendor` folder instead of crates.io:
   ```toml
   [source.crates-io]
   replace-with = "vendored-sources"

   [source.vendored-sources]
   directory = "vendor"
   ```

3. **Commit and Push the Submodule Assets:**
   Commit and push the `vendor/` directory and `.cargo/config.toml` changes inside the submodule:
   ```bash
   git add .cargo/config.toml vendor/
   git commit -m "Vendor Rust dependencies for F-Droid compliance"
   git push origin main
   ```
   *Note: Ensure the main repository's submodule pointer is updated to point to this new commit.*
