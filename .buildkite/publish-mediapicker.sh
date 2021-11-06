#!/bin/bash

set -euo pipefail

./gradlew \
    :mediapicker:prepareToPublishToS3 $(prepare_to_publish_to_s3_params) \
    :mediapicker:publish

# Add meta-data for the published version so we can use it in subsequent steps
cat ./mediapicker/build/published-version.txt | buildkite-agent meta-data set "PUBLISHED_MEDIAPICKER_VERSION"
