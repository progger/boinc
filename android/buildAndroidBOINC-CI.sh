#!/bin/sh
set -e

# This file is part of BOINC.
# http://boinc.berkeley.edu
# Copyright (C) 2021 University of California
#
# BOINC is free software; you can redistribute it and/or modify it
# under the terms of the GNU Lesser General Public License
# as published by the Free Software Foundation,
# either version 3 of the License, or (at your option) any later version.
#
# BOINC is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the GNU Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License
# along with BOINC.  If not, see <http://www.gnu.org/licenses/>.
#

# When you want to invalidate openssl and curl without change their versions.
export REV=1
export ARMV6_REV=1
export OPENSSL_VERSION=1.0.2s
export CURL_VERSION=7.62.0
export NDK_VERSION=21d
export NDK_ARMV6_VERSION=15c

# checks if a given path is canonical (absolute and does not contain relative links)
# from http://unix.stackexchange.com/a/256437
isPathCanonical() {
  case "x$1" in
    (x*/..|x*/../*|x../*|x*/.|x*/./*|x./*)
        rc=1
        ;;
    (x/*)
        rc=0
        ;;
    (*)
        rc=1
        ;;
  esac
  return $rc
}

doclean=""
cache_dir=""
arch=""
component=""
silent=""
verbose="${VERBOSE:-no}"
ci=""
build_with_vcpkg="no"

while [ $# -gt 0 ]; do
    key="$1"
    case $key in
        --cache_dir)
        cache_dir="$2"
        shift
        ;;
        --build_dir)
        build_dir="$2"
        shift
        ;;
        --clean)
        doclean="yes"
        ;;
        --arch)
        arch="$2"
        shift
        ;;
        --component)
        component="$2"
        shift
        ;;
        --with-vcpkg)
        build_with_vcpkg="yes"
        ;;
        --silent)
        silent="yes"
        ;;
        --verbose)
        verbose="yes"
        ;;
        --ci)
        ci="yes"
        ;;
        *)
        echo "unrecognized option $key"
        ;;
    esac
    shift # past argument or value
done

cd ../
vcpkg_ports_dir="$(pwd)/3rdParty/vcpkg_ports"
cd android/

if [ "x$cache_dir" != "x" ]; then
    if  ! ( isPathCanonical "$cache_dir" && [ "$cache_dir" != "/" ] ); then
        echo "cache_dir must be an absolute path without ./ or ../ in it"
        exit 1
    fi
else
    cd ../
    cache_dir="$(pwd)/3rdParty/buildCache/android"
    cd android/
fi

PREFIX="$cache_dir/android-tc"

if [ "x$build_dir" != "x" ]; then
    if isPathCanonical "$build_dir" && [ "$build_dir" != "/" ]; then
         BUILD_DIR="$build_dir"
     else
         echo "build_dir must be an absolute path without ./ or ../ in it"
         exit 1
     fi
else
    cd ../
    BUILD_DIR="$(pwd)/3rdParty/android"
    cd android/
fi

mkdir -p "${PREFIX}"
mkdir -p "${BUILD_DIR}"

if [ "${doclean}" = "yes" ]; then
    echo "cleaning cache dir"
    rm -rf "${cache_dir}"
    mkdir -p "${cache_dir}"
    echo "cleaning build dir"
    rm -rf "${BUILD_DIR}"
    mkdir -p "${BUILD_DIR}"
    echo "cleaning downloaded cache files"
    rm -f /tmp/ndk_${NDK_VERSION}.zip
    rm -f /tmp/ndk_armv6_${NDK_ARMV6_VERSION}.zip
    rm -f /tmp/openssl_${OPENSSL_VERSION}.tgz
    rm -f /tmp/curl_${CURL_VERSION}.tgz
fi

if [ "${silent}" = "yes" ]; then
    export STDOUT_TARGET="/dev/null"
fi

export NDK_FLAGFILE="$PREFIX/NDK-${NDK_VERSION}-${REV}_done"
export NDK_ARMV6_FLAGFILE="$PREFIX/NDK-${NDK_ARMV6_VERSION}-armv6-${ARMV6_REV}_done"
export NDK_ROOT=$BUILD_DIR/android-ndk-r${NDK_VERSION}
export NDK_ARMV6_ROOT=$BUILD_DIR/android-ndk-r${NDK_ARMV6_VERSION}
export OPENSSL_SRC=$BUILD_DIR/openssl-${OPENSSL_VERSION}
export CURL_SRC=$BUILD_DIR/curl-${CURL_VERSION}
export VCPKG_ROOT="$BUILD_DIR/vcpkg"
export ANDROID_TC=$PREFIX
export VERBOSE=$verbose
export CI=$ci
export BUILD_WITH_VCPKG=$build_with_vcpkg

if [ "$arch" = armv6 ]; then
    export CURL_FLAGFILE="$PREFIX/curl-${CURL_VERSION}-${NDK_ARMV6_VERSION}-${arch}_done"
    export OPENSSL_FLAGFILE="$PREFIX/openssl-${OPENSSL_VERSION}-${NDK_ARMV6_VERSION}-${arch}_done"
    export ANDROID_TC_FLAGFILE="$PREFIX/ANDROID_TC_WITH_NDK-${NDK_ARMV6_VERSION}-${arch}-${ARMV6_REV}_done"
else
    export CURL_FLAGFILE="$PREFIX/curl-${CURL_VERSION}-${NDK_VERSION}-${arch}_done"
    export OPENSSL_FLAGFILE="$PREFIX/openssl-${OPENSSL_VERSION}-${NDK_VERSION}-${arch}_done"
    export ANDROID_TC_FLAGFILE="$PREFIX/ANDROID_TC_WITH_NDK-${NDK_VERSION}-${arch}-${REV}_done"
fi

createNDKFolder()
{
    rm -rf "$BUILD_DIR/android-ndk-r${NDK_VERSION}"
    wget -c --no-verbose -O /tmp/ndk_${NDK_VERSION}.zip https://dl.google.com/android/repository/android-ndk-r${NDK_VERSION}-linux-x86_64.zip
    unzip -qq /tmp/ndk_${NDK_VERSION}.zip -d $BUILD_DIR
}

createNDKARMV6Folder()
{
    rm -rf "$BUILD_DIR/android-ndk-r${NDK_ARMV6_VERSION}"
    wget -c --no-verbose -O /tmp/ndk_armv6_${NDK_ARMV6_VERSION}.zip https://dl.google.com/android/repository/android-ndk-r${NDK_ARMV6_VERSION}-linux-x86_64.zip
    unzip -qq /tmp/ndk_armv6_${NDK_ARMV6_VERSION}.zip -d $BUILD_DIR
}

if [ ! -e "${NDK_FLAGFILE}" ]; then
    createNDKFolder
    touch "${NDK_FLAGFILE}"
fi

if [ ! -e "${NDK_ARMV6_FLAGFILE}" ]; then
    createNDKARMV6Folder
    touch "${NDK_ARMV6_FLAGFILE}"
fi

if [ ! -d $NDK_ROOT ]; then
    createNDKFolder
fi

if [ ! -d $NDK_ARMV6_ROOT ]; then
    createNDKARMV6Folder
fi

if [ ! -e "${ANDROID_TC_FLAGFILE}" ]; then
    rm -rf "${PREFIX}/${arch}"
    echo delete "${PREFIX}/${arch}"
    rm -rf "${OPENSSL_FLAGFILE}"
    rm -rf "${CURL_FLAGFILE}"
    touch ${ANDROID_TC_FLAGFILE}
fi

if [ ! -e "${OPENSSL_FLAGFILE}" ]; then
    rm -rf "$BUILD_DIR/openssl-${OPENSSL_VERSION}"
    wget -c --no-verbose -O /tmp/openssl_${OPENSSL_VERSION}.tgz https://www.openssl.org/source/openssl-${OPENSSL_VERSION}.tar.gz
    tar xzf /tmp/openssl_${OPENSSL_VERSION}.tgz --directory=$BUILD_DIR
fi

if [ ! -e "${CURL_FLAGFILE}" ]; then
    rm -rf "$BUILD_DIR/curl-${CURL_VERSION}"
    wget -c --no-verbose -O /tmp/curl_${CURL_VERSION}.tgz https://curl.haxx.se/download/curl-${CURL_VERSION}.tar.gz
    tar xzf /tmp/curl_${CURL_VERSION}.tgz --directory=$BUILD_DIR
fi

packegesToInstall()
{
    pkgs="rappture"
    echo $(packegesList $1 $pkgs)
}

packegesList()
{
    arch=$1
    shift
    list_pkgs=""
    while [ $# -gt 0 ]; do
        if [ $arch = "armv6" ]; then
            list_pkgs="$list_pkgs $1:armv6-android"
        fi
        if [ $arch = "arm" ]; then
            list_pkgs="$list_pkgs $1:arm-android"
        fi
        if [ $arch = "neon" ]; then
            list_pkgs="$list_pkgs $1:arm-android-neon"
        fi
        if [ $arch = "arm64" ]; then
            list_pkgs="$list_pkgs $1:arm64-android"
        fi
        if [ $arch = "x86" ]; then
            list_pkgs="$list_pkgs $1:x86-android"
        fi
        if [ $arch = "x86_64" ]; then
            list_pkgs="$list_pkgs $1:x64-android"
        fi
        shift
    done
    echo $list_pkgs
}

if [ $component = "apps" -a $build_with_vcpkg = "yes" ]; then
    export XDG_CACHE_HOME=$cache_dir/vcpkgcache/
    vcpkg_flags="--overlay-triplets=$vcpkg_ports_dir/triplets/default --clean-after-build"
    if [ ! -d "$VCPKG_ROOT" ]; then
        mkdir -p $BUILD_DIR
        git -C $BUILD_DIR clone https://github.com/microsoft/vcpkg
    fi
    if [ ! -e /tmp/vcpkg_updated ]; then
        git -C $VCPKG_ROOT reset --hard
        git -C $VCPKG_ROOT pull
        $VCPKG_ROOT/bootstrap-vcpkg.sh
        touch /tmp/vcpkg_updated
    fi
    if [ $arch = "armv6" ]; then
        export ANDROID_NDK_HOME=$NDK_ARMV6_ROOT

        $VCPKG_ROOT/vcpkg install $(packegesToInstall $arch) $vcpkg_flags
    fi
    if [ $arch = "arm" ]; then
        export ANDROID_NDK_HOME=$NDK_ROOT

        $VCPKG_ROOT/vcpkg install $(packegesToInstall $arch) $vcpkg_flags
    fi
    if [ $arch = "arm" ]; then
        export ANDROID_NDK_HOME=$NDK_ROOT

        $VCPKG_ROOT/vcpkg install $(packegesToInstall neon) $vcpkg_flags
    fi
    if [ $arch = "arm64" ]; then
        export ANDROID_NDK_HOME=$NDK_ROOT

        $VCPKG_ROOT/vcpkg install $(packegesToInstall $arch) $vcpkg_flags
    fi
    if [ $arch = "x86" ]; then
        export ANDROID_NDK_HOME=$NDK_ROOT

        $VCPKG_ROOT/vcpkg install $(packegesToInstall $arch) $vcpkg_flags
    fi
    if [ $arch = "x86_64" ]; then
        export ANDROID_NDK_HOME=$NDK_ROOT

        $VCPKG_ROOT/vcpkg install $(packegesToInstall $arch) $vcpkg_flags
    fi

    $VCPKG_ROOT/vcpkg upgrade --no-dry-run
fi

list_apps_name="boinc_gahp uc2 ucn multi_thread sleeper worker wrapper wrappture_example fermi"

NeonTest()
{
    while [ $# -gt 0 ]; do
        echo "NeonTest: readelf -A" "$1"
        if [ $(readelf -A $(find $ANDROID_TC/${arch} "../samples" -type f -name "$1") | grep -i neon | head -c1 | wc -c) -ne 0 ]; then
            echo $(readelf -A $(find $ANDROID_TC/${arch} "../samples" -type f -name "$1") | grep -i neon)
            echo [ERROR] "$1" contains neon optimization
            exit 1
        fi
        shift
    done
}

NeonTestClient()
{
    NeonTest libcrypto.a libssl.a libcurl.a
}

NeonTestLibs()
{
    NeonTest libboinc.a libboinc_api.a libboinc_opencl.a libboinc_zip.a
}

Armv6Test()
{
    while [ $# -gt 0 ]; do
        echo "Armv6Test: readelf -A" "$1"
        if [ $(readelf -A $(find $ANDROID_TC/armv6 "BOINC/app/src/main/assets/armeabi" "../samples" -type f -name "$1") | grep -i "Tag_CPU_arch: v6" | head -c1 | wc -c) -eq 0 ]; then
            echo $(readelf -A $(find $ANDROID_TC/armv6 "BOINC/app/src/main/assets/armeabi" "../samples" -type f -name "$1") | grep -i "Tag_CPU_arch:")
            echo [ERROR] "$1" is not armv6 cpu arch
            exit 1
        fi
        shift
    done
}

Armv6TestClient()
{
    Armv6Test libcrypto.a libssl.a libcurl.a boinc
}

Armv6TestLibs()
{
    Armv6Test libboinc.a libboinc_api.a libboinc_opencl.a libboinc_zip.a
}

Armv6TestApps()
{
    Armv6Test $list_apps_name
}

NeonTestApps()
{
    NeonTest  $list_apps_name
}

RenameAllApps()
{
    list_apps="../samples/condor/ boinc_gahp
                ../samples/example_app/ uc2 
                ../samples/example_app/ ucn
                ../samples/multi_thread/ multi_thread
                ../samples/sleeper/ sleeper
                ../samples/worker/ worker
                ../samples/wrapper/ wrapper
                ../samples/wrappture/ wrappture_example
                ../samples/wrappture/ fermi
                "

    RenameApp $1 $list_apps
}

RenameApp()
{
    arch=$1
    shift
    while [ $# -gt 0 ]; do
        mv "$1$2" "$1android_${arch}_$2"
        shift
        shift
    done
}

case "$arch" in
    "armv6")
        ./build_androidtc_armv6.sh
        case "$component" in
            "client")
                ./build_openssl_armv6.sh
                ./build_curl_armv6.sh
                ./build_boinc_armv6.sh
                NeonTestClient
                Armv6TestClient
                exit 0
            ;;
            "libs")
                ./build_libraries_armv6.sh
                NeonTestLibs
                Armv6TestLibs
                exit 0
            ;;
            "apps")
                ./build_openssl_armv6.sh
                ./build_curl_armv6.sh
                ./build_libraries_armv6.sh
                ./build_example_armv6.sh
                NeonTestLibs
                NeonTestApps
                Armv6TestLibs
                Armv6TestApps
                if [ "$ci" = "yes" ]; then
                    RenameAllApps armv6
                fi
                exit 0
            ;;
            *)
                echo "unknown component: $component"
                exit 1
            ;;
        esac
    ;;
    "arm")
        case "$component" in
            "client")
                ./build_openssl_arm.sh
                ./build_curl_arm.sh
                ./build_boinc_arm.sh
                NeonTestClient
                exit 0
            ;;
            "libs")
                ./build_libraries_arm.sh
                NeonTestLibs
                exit 0
            ;;
            "apps")
                ./build_openssl_arm.sh
                ./build_curl_arm.sh
                ./build_libraries_arm.sh
                ./build_example_arm.sh
                NeonTestLibs
                NeonTestApps
                if [ "$ci" = "yes" ]; then
                    RenameAllApps arm
                fi
                exit 0
            ;;
            *)
                echo "unknown component: $component"
                exit 1
            ;;
        esac
    ;;
    "arm64")
        case "$component" in
            "client")
                ./build_openssl_arm64.sh
                ./build_curl_arm64.sh
                ./build_boinc_arm64.sh
                exit 0
            ;;
            "libs")
                ./build_libraries_arm64.sh
                exit 0
            ;;
            "apps")
                ./build_openssl_arm64.sh
                ./build_curl_arm64.sh
                ./build_libraries_arm64.sh
                ./build_example_arm64.sh
                if [ "$ci" = "yes" ]; then
                    RenameAllApps arm64
                fi
                exit 0
            ;;
            *)
                echo "unknown component: $component"
                exit 1
            ;;
        esac
    ;;
    "x86")
        case "$component" in
            "client")
                ./build_openssl_x86.sh
                ./build_curl_x86.sh
                ./build_boinc_x86.sh
                exit 0
            ;;
            "libs")
                ./build_libraries_x86.sh
                exit 0
            ;;
            "apps")
                ./build_openssl_x86.sh
                ./build_curl_x86.sh
                ./build_libraries_x86.sh
                ./build_example_x86.sh
                if [ "$ci" = "yes" ]; then
                    RenameAllApps x86
                fi
                exit 0
            ;;
            *)
                echo "unknown component: $component"
                exit 1
            ;;
        esac
    ;;
    "x86_64")
        case "$component" in
            "client")
                ./build_openssl_x86_64.sh
                ./build_curl_x86_64.sh
                ./build_boinc_x86_64.sh
                exit 0
            ;;
            "libs")
                ./build_libraries_x86_64.sh
                exit 0
            ;;
            "apps")
                ./build_openssl_x86_64.sh
                ./build_curl_x86_64.sh
                ./build_libraries_x86_64.sh
                ./build_example_x86_64.sh
                if [ "$ci" = "yes" ]; then
                    RenameAllApps x86_64
                fi
                exit 0
            ;;
            *)
                echo "unknown component: $component"
                exit 1
            ;;
        esac
    ;;
esac

echo "unknown architecture: $arch"
exit 1
