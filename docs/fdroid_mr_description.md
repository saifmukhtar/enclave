### Required
* [x] The app complies with the [inclusion criteria](https://f-droid.org/wiki/page/Inclusion_Policy):
  * *WebRTC Library FOSS-Compliance:* Instead of standard Google WebRTC binaries, Enclave consumes the open-source, F-Droid approved `im.conversations.webrtc:webrtc-android:129.0.0` library, which contains zero proprietary components or Google Play Services dependencies.
* [x] The original app author has been notified (and does not oppose the inclusion) — *I am the original author of the application.*
* [x] All related fdroiddata and RFP issues have been referenced in this merge request — *None (this is the initial submission).*
* [x] Builds with `fdroid build` and all pipelines pass — *Verified locally with a successful build run using F-Droid server tools.*
* [x] There is an issue tracker and contact info of the author so that we can report bugs and contact the author:
  * **Issue Tracker:** https://github.com/saifmukhtar/enclave/issues
  * **Author Email:** saifmukhtar20@gmail.com

### Strongly Recommended
* [x] The upstream app source code repo contains the app metadata in a Fastlane folder structure under: `apps/android/app/src/main/fastlane/metadata/android/en-US/` containing:
  * Title (`title.txt`)
  * Short Description (`short_description.txt`)
  * Full Description (`full_description.txt`)
* [x] Releases are tagged and auto update is enabled:
  * **Build Target Tag:** `v3.0.0` (versionCode 4)
  * **Update Check:** Configured for automated Git release tags with `AutoUpdateMode: Version` and `UpdateCheckMode: Tags`.

### Suggested
* [x] External repos are added as git submodules instead of srclibs:
  * The `libsignal-src` submodule (tracked at upstream tag `v0.39.2`) is included as a native Git submodule under the root tree, with Rust dependencies safely vendored to allow networkless compilation on the build server.
* [ ] Enable Reproducible Builds:
  * `No, I don't want this.` (The JNI library binaries are compiled dynamically during prebuild, so the final APK should be signed with the F-Droid repository key).
* [ ] Multiple apks for native code:
  * *We are building a single universal APK supporting arm64-v8a, armeabi-v7a, x86_64, and x86 architectures for ease of deployment.*

/label New App
