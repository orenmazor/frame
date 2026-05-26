# Minimal image with Android Debug Bridge (adb).
# Alpine keeps the image small; android-tools ships adb and fastboot from Alpine community.
FROM alpine:3.21

RUN apk add --no-cache \
    android-tools \
    ca-certificates

# Default: print adb version (override command/args in Kubernetes or docker run).
ENTRYPOINT ["adb"]
CMD ["version"]
