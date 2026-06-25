#!/system/bin/sh
# This script runs inside the proot-distro environment via busybox sh
# It sets up the Debian rootfs after extraction

ROOTFS_DIR="$1"
VNC_PASSWORD="${2:-debian}"

# Create debian user
if ! grep -q "^debian:" "$ROOTFS_DIR/etc/passwd" 2>/dev/null; then
    echo "debian:x:1000:1000:Debian User,,,:/home/debian:/bin/bash" >> "$ROOTFS_DIR/etc/passwd"
    echo "debian:x:1000:" >> "$ROOTFS_DIR/etc/group"
fi

# Create home directory
mkdir -p "$ROOTFS_DIR/home/debian/.vnc"
mkdir -p "$ROOTFS_DIR/home/debian/Desktop"

# Create VNC xstartup
cat > "$ROOTFS_DIR/home/debian/.vnc/xstartup" << 'EOF'
#!/bin/bash
export DISPLAY=:1
export HOME=/home/debian
export USER=debian
unset SESSION_MANAGER
unset DBUS_SESSION_BUS_ADDRESS
startxfce4 &
EOF
chmod +x "$ROOTFS_DIR/home/debian/.vnc/xstartup"

# Set VNC password
echo "$VNC_PASSWORD" | vncpasswd -f > "$ROOTFS_DIR/home/debian/.vnc/passwd"
chmod 600 "$ROOTFS_DIR/home/debian/.vnc/passwd"

# Ensure resolv.conf
echo "nameserver 8.8.8.8" > "$ROOTFS_DIR/etc/resolv.conf"
echo "nameserver 1.1.1.1" >> "$ROOTFS_DIR/etc/resolv.conf"
chmod 644 "$ROOTFS_DIR/etc/resolv.conf"

# Fix permissions
chown -R 1000:1000 "$ROOTFS_DIR/home/debian"
