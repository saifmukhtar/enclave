# F-Droid Submission Guide for Enclave

This guide outlines the step-by-step process, prerequisites, and metadata configuration required to submit Enclave (`dev.saifmukhtar.enclave`) to the official F-Droid repository.

## 1. Prerequisites Checklist
Before submitting, ensure you have:
- **Public Git Repository**: A public version control repository containing the `enclave-ui` code (e.g., on GitHub or GitLab).
- **GitLab Account**: Since F-Droid's build pipeline is hosted on GitLab, you need an account to fork the metadata repository and submit your merge request.
- **Fastlane Metadata Structure (Optional but highly recommended)**: F-Droid can automatically ingest screenshots, descriptions, icons, and changelogs if your project structures them in `app/src/main/fastlane/metadata/android/`.

---

## 2. App & Package Configuration Details
- **Application ID / Package Name**: `dev.saifmukhtar.enclave`
- **Root Project Subdirectory**: `apps/android` (Since the code resides in a subdirectory of your main monorepo).
- **Gradle Properties Flag**: `fdroid=true` (Used to trigger our conditional dependency block).

---

## 3. Creating the F-Droid Metadata File
You must create a YAML file named exactly `dev.saifmukhtar.enclave.yml`. Below is the complete template configured specifically for Enclave, including compiling `libsignal` from source via F-Droid's `srclibs` system.

```yaml
Categories:
  - Internet
  - Security
License: AGPL-3.0-only
SourceCode: https://github.com/[YOUR_USERNAME]/enclave
IssueTracker: https://github.com/[YOUR_USERNAME]/enclave/issues
WebSite: https://github.com/[YOUR_USERNAME]/enclave

RepoType: git
Repo: https://github.com/[YOUR_USERNAME]/enclave.git

Builds:
  - versionName: 1.1.0
    versionCode: 2
    commit: v1.1.0
    subdir: apps/android
    srclibs:
      - libsignal@v0.39.2
    prebuild: |
      # Save the absolute path of Enclave's Android directory
      ENCLAVE_DIR=$(pwd)
      
      # Build libsignal from source cleanly
      cd $$libsignal$$/java
      echo "sdk.dir=$ANDROID_HOME" > local.properties
      ./gradlew assembleRelease -x test
      
      # Navigate back to Enclave and copy artifacts safely
      cd $ENCLAVE_DIR
      mkdir -p app/libs
      cp $$libsignal$$/java/android/build/outputs/aar/*-release.aar app/libs/
      cp $$libsignal$$/java/client/build/libs/*.jar app/libs/
    gradle:
      - yes
    gradleprops:
      - fdroid=true

AutoUpdateMode: Version
UpdateCheckMode: Tags
```

---

## 4. How to Submit
Follow these sequential steps to submit the metadata file to F-Droid:

### Step 1: Fork and Clone `fdroiddata`
1. Go to the official [fdroiddata GitLab repository](https://gitlab.com/fdroid/fdroiddata).
2. Click **Fork** to create a copy under your GitLab namespace.
3. Clone your fork locally:
   ```bash
   git clone https://gitlab.com/your-username/fdroiddata.git
   cd fdroiddata
   ```

### Step 2: Add your Metadata File
1. Create a new branch for your app submission:
   ```bash
   git checkout -b add-enclave
   ```
2. Write the metadata YAML content to `metadata/dev.saifmukhtar.enclave.yml`.
3. Commit and push the changes:
   ```bash
   git add metadata/dev.saifmukhtar.enclave.yml
   git commit -m "Add dev.saifmukhtar.enclave (Enclave)"
   git push origin add-enclave
   ```

### Step 3: Open a Merge Request
1. Go to your fork page on GitLab.
2. GitLab will present a prompt to open a **Merge Request**.
3. Fill out the submission template (F-Droid will ask you to confirm that the app has no proprietary components, ads, or tracking).
4. Submit the Merge Request. F-Droid's automated build runner will clone your repo and attempt to compile the app using the recipe in your `.yml` file.

---

## 5. Testing the Build Locally (Optional)
If you have a Linux machine, you can run F-Droid's build server locally to test your recipe before submitting:
1. Install `fdroidserver`:
   ```bash
   sudo apt install fdroidserver
   ```
2. Inside your cloned `fdroiddata` folder, run the lint check:
   ```bash
   fdroid readmeta
   fdroid rewritemeta dev.saifmukhtar.enclave
   fdroid lint dev.saifmukhtar.enclave
   ```
3. Run a test build inside a containerized sandbox:
   ```bash
   fdroid build -v -s dev.saifmukhtar.enclave
   ```

---

## 6. Managing the libsignal Dependency (Two Options)

Since `libsignal` is not globally defined in the F-Droid Data repository, you can choose one of the following two standard patterns to pass F-Droid verification:

### Option A: The Git Submodules Approach (Highly Recommended)
F-Droid developers officially encourage using Git submodules over `srclibs` for new submissions due to better reproducibility, security tracking, and self-contained builds.

1. **Add libsignal as a Submodule in Enclave**:
   In your Enclave repository root, run:
   ```bash
   git submodule add --force --name libsignal-src https://github.com/signalapp/libsignal.git libsignal-src
   cd libsignal-src
   git checkout v0.39.2
   cd ..
   git add libsignal-src .gitmodules
   git commit -m "chore: add libsignal as git submodule"
   ```
2. **Update your F-Droid Metadata**:
   Configure F-Droid to automatically fetch submodules by adding `submodules: yes` and replacing `$$libsignal$$` with the local submodule path relative to the subdirectory:
   ```yaml
   Builds:
     - versionName: 1.1.0
       versionCode: 2
       commit: v1.1.0
       subdir: apps/android
       submodules: yes
       prebuild: |
         # From apps/android, the submodule lives two directories up
         cd ../../libsignal-src/java
         echo "sdk.dir=$ANDROID_HOME" > local.properties
         ./gradlew assembleRelease -x test
         
         # Return to the app module and inject binaries
         cd ../../apps/android
         mkdir -p app/libs
         cp ../../libsignal-src/java/android/build/outputs/aar/*-release.aar app/libs/
         cp ../../libsignal-src/java/client/build/libs/*.jar app/libs/
       gradle:
         - yes
       gradleprops:
         - fdroid=true
   ```

### Option B: The Custom Srclib Submission Approach
If you prefer not to include the submodule inside your main code repository, you can submit the `libsignal` source library definition globally alongside your app:

1. **Create `srclibs/libsignal.yml` in your fork of `fdroiddata`**:
   Write the following content to `srclibs/libsignal.yml`:
   ```yaml
   RepoType: git
   Repo: https://github.com/signalapp/libsignal.git
   ```
2. **Reference it in `metadata/dev.saifmukhtar.enclave.yml`**:
   Ensure `srclibs` references `libsignal@v0.39.2` as set up in Section 3 above. Both files (`metadata/dev.saifmukhtar.enclave.yml` and `srclibs/libsignal.yml`) should be submitted in the same GitLab Merge Request.
