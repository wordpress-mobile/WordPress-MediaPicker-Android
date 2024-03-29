#!/bin/bash

set -euo pipefail

# Retrieve data from previous steps
PUBLISHED_MEDIAPICKER_DOMAIN_VERSION=$(buildkite-agent meta-data get "PUBLISHED_MEDIAPICKER_DOMAIN_VERSION")

./gradlew \
    -PmediaPickerDomainVersion="$PUBLISHED_MEDIAPICKER_DOMAIN_VERSION" \
    :mediapicker:source-camera:prepareToPublishToS3 $(prepare_to_publish_to_s3_params) \
    :mediapicker:source-camera:publish
