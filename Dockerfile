FROM ubuntu:24.04

ARG ANDROID_CMDLINE_TOOLS_REVISION=11076708
ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_API_LEVEL=35 \
    ANDROID_BUILD_TOOLS_VERSION=35.0.0

ENV PATH="${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        openjdk-17-jdk-headless \
        unzip \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && curl -fsSL \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_REVISION}_latest.zip" \
        -o /tmp/android-commandline-tools.zip \
    && mkdir -p /tmp/android-commandline-tools \
    && unzip -q /tmp/android-commandline-tools.zip -d /tmp/android-commandline-tools \
    && mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && mv /tmp/android-commandline-tools/cmdline-tools/* "${ANDROID_SDK_ROOT}/cmdline-tools/latest/" \
    && rm -rf /tmp/android-commandline-tools /tmp/android-commandline-tools.zip

COPY scripts/bootstrap-android.sh /usr/local/bin/bootstrap-android
RUN chmod +x /usr/local/bin/bootstrap-android \
    && bootstrap-android --sdk-only

WORKDIR /workspace

CMD ["bash"]
