#!/bin/bash

set -euo pipefail

# Retrieve data from previous steps
PUBLISHED_MEDIAPICKER_VERSION=$(buildkite-agent meta-data get "PUBLISHED_MEDIAPICKER_VERSION")

./gradlew \
    -PmediapickerVersion="PUBLISHED_MEDIAPICKER_VERSION" \
    :mediapicker:domain:prepareToPublishToS3 $(prepare_to_publish_to_s3_params) \
    :mediapicker:domain:publish
