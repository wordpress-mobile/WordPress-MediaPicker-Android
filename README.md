# WordPress-MediaPicker-Android

## Use the library in your project

* In your `build.gradle`:

```groovy
repositories {
    maven {
        url 'https://a8c-libs.s3.amazonaws.com/android'
        content {
            includeGroup "org.wordpress"
            includeGroup "org.wordpress.mediapicker"
        }
    }
}

dependencies {
    implementation 'org.wordpress:mediapicker:{version}'
    // implementation 'org.wordpress:mediapicker:domain:{version}'
    // implementation 'org.wordpress:mediapicker:source-device:{version}'
    // implementation 'org.wordpress:mediapicker:source-gif:{version}'
    // implementation 'org.wordpress:mediapicker:source-camera:{version}'
    // implementation 'org.wordpress:mediapicker:source-wordpress:{version}'
}
```

## Publishing a new version

In the following cases, Buildkite will publish a new version with the following format to our remote Maven repo:

* For each commit in an open PR: `<PR-number>-<commit full SHA1>`
* Each time a PR is merged to `trunk`: `trunk-<commit full SHA1>`
* Each time a new tag is created: `{tag-name}`

_Note that forked PRs will not trigger a Buildkite job._
