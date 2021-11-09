#!/bin/bash

set -euo pipefail

./gradlew \
    :mediapicker:domain:prepareToPublishToS3 $(prepare_to_publish_to_s3_params) \
    :mediapicker:domain:publish

# Add meta-data for the published version so we can use it in subsequent steps
cat ./MediaPicker/domain/build/published-version.txt | buildkite-agent meta-data set "PUBLISHED_MEDIAPICKER_DOMAIN_VERSION"
