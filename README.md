# GH Pusher

Push, pull and auto-push to GitHub from your phone. No git needed (uses the GitHub REST API).
Single-file web app (`www/index.html`) wrapped with Capacitor so GitHub Actions can build an Android APK.

## Build the APK with GitHub
1. Create a repo and push **this whole folder** to branch `main`.
   Your token needs the **workflow** scope (classic) or **Workflows: Read and write** (fine-grained),
   because the project contains `.github/workflows/android.yml`.
2. Open the **Actions** tab: *Build Android APK* runs by itself (5-8 minutes).
3. Download `GHPusher.apk` from **Releases** on your phone and install it (allow "unknown sources").

## Pages
- **Account** - token, saved on the device.
- **Push** - zip / files / folder to a repo. Auto-unpack zip, branch dropdown (default branch first), target folder, Mirror mode.
  Unchanged files are not uploaded again; if nothing changed no commit is made.
- **Pull** - paste `https://github.com/user/repo.git`, choose the branch and a directory. Unpack or keep a .zip, Override on/off.
  It downloads a snapshot of the branch (no `.git` history).
- **Auto Push** - watches a zip / files / folder. Any change of name, size or modified time triggers a push after it settles.
  On Android it runs a foreground service (notification) so it keeps working with the screen off.
- **Utils** - Token checker (owner, scopes, expiry, rate limit, orgs, reachable repos), Create repository, Builds & APK releases (+ run workflow).

## Notes
- Android 15 limits "data sync" foreground services to about 6 hours per run; just start Auto Push again.
- If the system still kills the app, disable battery optimisation for GH Pusher.
- In a normal browser (no APK) Push and Pull work. Auto Push needs the APK or desktop Chrome/Edge.
- Run on a computer: `npx http-server www -p 8080` then open http://localhost:8080

## Actions & releases
- After every push the app looks for workflow runs triggered by that commit and shows jobs, steps, logs and artifacts (Push and Auto Push pages).
- Utils > Actions & releases: runs and releases, 5 at a time (scroll or press Load more). Tap a name to expand it, tap a file to download.
- Fine-grained tokens need **Actions: Read** for logs/artifacts. Classic tokens need `repo`.
