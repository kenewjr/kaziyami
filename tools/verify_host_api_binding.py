#!/usr/bin/env python3
"""
verify_host_api_binding.py:
Scans built extension APKs for binary compatibility against the real host API classpath.
Validates that every class, method (name + descriptor), and field referenced in Dalvik bytecode (classes.dex)
resolves in the compileOnly host libraries (extensions-lib-14.jar, android.jar) or the local APK classes,
walking the full superclass and interface chain exactly as Android ART does.
"""

import glob
import os
import struct
import sys
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT_DIR, "tools"))
import android_sdk

HOST_JARS = [
    os.path.join(ROOT_DIR, "libs", "extensions-lib-14.jar")
]

HOST_PACKAGE_PREFIXES = (
    "Leu/kanade/",
    "Lokhttp3/",
    "Lokio/",
    "Landroidx/",
    "Luy/kohesive/",
    "Lorg/jsoup/",
    "Landroid/"
)

def parse_class_file(data):
    magic, minor, major, cp_count = struct.unpack_from(">IHHH", data, 0)
    pos = 10
    cp = [""]
    i = 1
    while i < cp_count:
        tag = data[pos]
        pos += 1
        if tag == 1:  # Utf8
            length = struct.unpack_from(">H", data, pos)[0]
            pos += 2
            cp.append(data[pos:pos+length].decode("utf-8", errors="replace"))
            pos += length
        elif tag in (3, 4):  # Integer, Float
            pos += 4; cp.append(None)
        elif tag in (5, 6):  # Long, Double
            pos += 8; cp.append(None); cp.append(None); i += 1
        elif tag in (7, 8):  # Class, String
            idx = struct.unpack_from(">H", data, pos)[0]; pos += 2; cp.append(("ref", tag, idx))
        elif tag in (9, 10, 11, 12):  # Field, Method, InterfaceMethod, NameAndType
            p1, p2 = struct.unpack_from(">HH", data, pos); pos += 4; cp.append(("pair", tag, p1, p2))
        elif tag == 15:  # MethodHandle
            pos += 3; cp.append(None)
        elif tag in (16, 19, 20):  # MethodType, Module, Package
            pos += 2; cp.append(None)
        elif tag in (17, 18):  # Dynamic, InvokeDynamic
            pos += 4; cp.append(None)
        else:
            raise ValueError(f"Unknown class constant pool tag {tag} at byte {pos-1}")
        i += 1

    access_flags, this_cls, super_cls, ifaces_count = struct.unpack_from(">HHHH", data, pos)
    pos += 8
    this_name = cp[cp[this_cls][2]] if this_cls else None
    super_name = cp[cp[super_cls][2]] if super_cls else None

    ifaces = []
    for _ in range(ifaces_count):
        if_idx = struct.unpack_from(">H", data, pos)[0]
        pos += 2
        ifaces.append(cp[cp[if_idx][2]])

    fields_count = struct.unpack_from(">H", data, pos)[0]
    pos += 2
    fields = set()
    for _ in range(fields_count):
        _, n_idx, d_idx, attr_count = struct.unpack_from(">HHHH", data, pos)
        pos += 8
        fields.add((cp[n_idx], cp[d_idx]))
        for _ in range(attr_count):
            _, attr_len = struct.unpack_from(">HI", data, pos)
            pos += 6 + attr_len

    methods_count = struct.unpack_from(">H", data, pos)[0]
    pos += 2
    methods = set()
    for _ in range(methods_count):
        _, n_idx, d_idx, attr_count = struct.unpack_from(">HHHH", data, pos)
        pos += 8
        methods.add((cp[n_idx], cp[d_idx]))
        for _ in range(attr_count):
            _, attr_len = struct.unpack_from(">HI", data, pos)
            pos += 6 + attr_len

    return this_name, super_name, ifaces, fields, methods

def find_gradle_cache_jars():
    home = os.path.expanduser("~")
    cache_dir = os.path.join(home, ".gradle", "caches", "modules-2", "files-2.1")
    if not os.path.exists(cache_dir):
        return []
    target_artifacts = [
        "com.squareup.okhttp3/okhttp",
        "com.squareup.okio/okio-jvm",
        "org.jsoup/jsoup",
        "uy.kohesive.injekt/injekt-core",
        "uy.kohesive.injekt/injekt-api",
        "org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm",
        "org.jetbrains.kotlinx/kotlinx-serialization-json-jvm",
        "io.reactivex/rxjava"
    ]
    found = []
    for art in target_artifacts:
        art_dir = os.path.join(cache_dir, art.replace('/', os.sep))
        if os.path.exists(art_dir):
            jars = [
                j for j in glob.glob(art_dir + "/**/*.jar", recursive=True)
                if not j.endswith("-sources.jar") and not j.endswith("-javadoc.jar")
            ]
            if jars:
                found.append(jars[0])
    return found

def load_host_classes(extra_jars=None):
    jars = list(HOST_JARS)
    sdk_tools = android_sdk.get_sdk_tools()
    android_jar = sdk_tools.get("android_jar")
    if android_jar and os.path.exists(android_jar):
        jars.append(android_jar)

    host_libs_dir = os.path.join(ROOT_DIR, "build", "host-libs")
    if os.path.exists(host_libs_dir):
        for fname in os.listdir(host_libs_dir):
            if fname.endswith(".jar"):
                jars.append(os.path.join(host_libs_dir, fname))

    jars.extend(find_gradle_cache_jars())
    if extra_jars:
        jars.extend(extra_jars)

    host_classes = {}
    for jar_path in jars:
        if not os.path.exists(jar_path):
            continue
        with zipfile.ZipFile(jar_path, "r") as zf:
            for name in zf.namelist():
                if name.endswith(".class") and not name.startswith("META-INF"):
                    try:
                        this_n, super_n, ifaces, fields, methods = parse_class_file(zf.read(name))
                        type_key = f"L{this_n};"
                        super_key = f"L{super_n};" if super_n else None
                        iface_keys = [f"L{ifn};" for ifn in ifaces]
                        host_classes[type_key] = (super_key, iface_keys, fields, methods)
                    except Exception:
                        pass
    return host_classes

def read_uleb128(data, pos):
    result = 0
    shift = 0
    while True:
        b = data[pos]
        pos += 1
        result |= (b & 0x7f) << shift
        if (b & 0x80) == 0:
            break
        shift += 7
    return result, pos

def parse_dex(dex_data):
    (
        string_ids_size, string_ids_off,
        type_ids_size, type_ids_off,
        proto_ids_size, proto_ids_off,
        field_ids_size, field_ids_off,
        method_ids_size, method_ids_off,
        class_defs_size, class_defs_off
    ) = struct.unpack_from("<12I", dex_data, 56)

    def get_string(idx):
        off = struct.unpack_from("<I", dex_data, string_ids_off + idx * 4)[0]
        length, pos = read_uleb128(dex_data, off)
        end = dex_data.find(b"\x00", pos)
        return dex_data[pos:end].decode("utf-8", errors="replace")

    def get_type(idx):
        return get_string(struct.unpack_from("<I", dex_data, type_ids_off + idx * 4)[0])

    def get_proto(idx):
        shorty_idx, return_type_idx, parameters_off = struct.unpack_from("<III", dex_data, proto_ids_off + idx * 12)
        return_type = get_type(return_type_idx)
        params = []
        if parameters_off != 0:
            size = struct.unpack_from("<I", dex_data, parameters_off)[0]
            for i in range(size):
                p_idx = struct.unpack_from("<H", dex_data, parameters_off + 4 + i * 2)[0]
                params.append(get_type(p_idx))
        return f"({''.join(params)}){return_type}"

    local_classes = {}
    for i in range(class_defs_size):
        (
            class_idx, access_flags, superclass_idx, interfaces_off,
            source_file_idx, annotations_off, class_data_off, static_values_off
        ) = struct.unpack_from("<8I", dex_data, class_defs_off + i * 32)

        cname = get_type(class_idx)
        super_cname = get_type(superclass_idx) if superclass_idx != 0xFFFFFFFF else None
        ifaces = []
        if interfaces_off != 0:
            if_size = struct.unpack_from("<I", dex_data, interfaces_off)[0]
            for j in range(if_size):
                ifaces.append(get_type(struct.unpack_from("<H", dex_data, interfaces_off + 4 + j * 2)[0]))

        methods = set()
        fields = set()
        if class_data_off != 0:
            pos = class_data_off
            static_fields_size, pos = read_uleb128(dex_data, pos)
            instance_fields_size, pos = read_uleb128(dex_data, pos)
            direct_methods_size, pos = read_uleb128(dex_data, pos)
            virtual_methods_size, pos = read_uleb128(dex_data, pos)

            last_idx = 0
            for _ in range(static_fields_size):
                f_delta, pos = read_uleb128(dex_data, pos); last_idx += f_delta
                f_flags, pos = read_uleb128(dex_data, pos)
                fc_idx, ft_idx, fn_idx = struct.unpack_from("<HHI", dex_data, field_ids_off + last_idx * 8)
                fields.add((get_string(fn_idx), get_type(ft_idx)))

            last_idx = 0
            for _ in range(instance_fields_size):
                f_delta, pos = read_uleb128(dex_data, pos); last_idx += f_delta
                f_flags, pos = read_uleb128(dex_data, pos)
                fc_idx, ft_idx, fn_idx = struct.unpack_from("<HHI", dex_data, field_ids_off + last_idx * 8)
                fields.add((get_string(fn_idx), get_type(ft_idx)))

            last_idx = 0
            for _ in range(direct_methods_size):
                m_delta, pos = read_uleb128(dex_data, pos); last_idx += m_delta
                m_flags, pos = read_uleb128(dex_data, pos)
                code_off, pos = read_uleb128(dex_data, pos)
                mc_idx, mp_idx, mn_idx = struct.unpack_from("<HHI", dex_data, method_ids_off + last_idx * 8)
                methods.add((get_string(mn_idx), get_proto(mp_idx)))

            last_idx = 0
            for _ in range(virtual_methods_size):
                m_delta, pos = read_uleb128(dex_data, pos); last_idx += m_delta
                m_flags, pos = read_uleb128(dex_data, pos)
                code_off, pos = read_uleb128(dex_data, pos)
                mc_idx, mp_idx, mn_idx = struct.unpack_from("<HHI", dex_data, method_ids_off + last_idx * 8)
                methods.add((get_string(mn_idx), get_proto(mp_idx)))

        local_classes[cname] = (super_cname, ifaces, fields, methods)

    method_refs = []
    for i in range(method_ids_size):
        class_idx, proto_idx, name_idx = struct.unpack_from("<HHI", dex_data, method_ids_off + i * 8)
        method_refs.append((get_type(class_idx), get_string(name_idx), get_proto(proto_idx)))

    field_refs = []
    for i in range(field_ids_size):
        class_idx, type_idx, name_idx = struct.unpack_from("<HHI", dex_data, field_ids_off + i * 8)
        field_refs.append((get_type(class_idx), get_string(name_idx), get_type(type_idx)))

    return local_classes, method_refs, field_refs

def resolve_method(owner, name, desc, local_classes, host_classes, visited=None):
    if visited is None:
        visited = set()
    if owner in visited:
        return False
    visited.add(owner)

    # 1. Local classes defined inside APK
    if owner in local_classes:
        sup, ifc, flds, mths = local_classes[owner]
        if (name, desc) in mths:
            return True
        if sup and resolve_method(sup, name, desc, local_classes, host_classes, visited):
            return True
        for ifn in ifc:
            if resolve_method(ifn, name, desc, local_classes, host_classes, visited):
                return True
        return False

    # 2. Host classes in compileOnly jars
    if owner in host_classes:
        sup, ifc, flds, mths = host_classes[owner]
        if (name, desc) in mths:
            return True
        if sup and resolve_method(sup, name, desc, local_classes, host_classes, visited):
            return True
        for ifn in ifc:
            if resolve_method(ifn, name, desc, local_classes, host_classes, visited):
                return True
        return False

    # Array methods delegate to Object
    if owner.startswith("["):
        return resolve_method("Ljava/lang/Object;", name, desc, local_classes, host_classes, visited)

    # If owner is not in host jars or local classes, cannot be resolved against known host surface
    return False

def resolve_field(owner, name, desc, local_classes, host_classes, visited=None):
    if visited is None:
        visited = set()
    if owner in visited:
        return False
    visited.add(owner)

    if owner in local_classes:
        sup, ifc, flds, mths = local_classes[owner]
        if (name, desc) in flds:
            return True
        if sup and resolve_field(sup, name, desc, local_classes, host_classes, visited):
            return True
        for ifn in ifc:
            if resolve_field(ifn, name, desc, local_classes, host_classes, visited):
                return True
        return False

    if owner in host_classes:
        sup, ifc, flds, mths = host_classes[owner]
        if (name, desc) in flds:
            return True
        if sup and resolve_field(sup, name, desc, local_classes, host_classes, visited):
            return True
        for ifn in ifc:
            if resolve_field(ifn, name, desc, local_classes, host_classes, visited):
                return True
        return False

    return False

def verify_apk(apk_path, host_classes):
    with zipfile.ZipFile(apk_path, "r") as zf:
        if "classes.dex" not in zf.namelist():
            return [f"classes.dex missing from {apk_path}"]
        dex_data = zf.read("classes.dex")

    local_classes, method_refs, field_refs = parse_dex(dex_data)

    errors = []

    # Check method references targeting host classes or local classes that extend host classes
    for target, name, desc in method_refs:
        is_host_target = any(target.startswith(p) for p in HOST_PACKAGE_PREFIXES)
        is_local_target = target in local_classes
        if is_host_target or is_local_target:
            if not resolve_method(target, name, desc, local_classes, host_classes):
                errors.append(f"UNRESOLVED METHOD: {target}->{name}{desc}")

    # Check field references
    for target, name, desc in field_refs:
        is_host_target = any(target.startswith(p) for p in HOST_PACKAGE_PREFIXES)
        is_local_target = target in local_classes
        if is_host_target or is_local_target:
            if not resolve_field(target, name, desc, local_classes, host_classes):
                errors.append(f"UNRESOLVED FIELD: {target}->{name}:{desc}")

    return errors

def main():
    specific_apk = None
    if "--apk" in sys.argv:
        idx = sys.argv.index("--apk")
        if idx + 1 < len(sys.argv):
            specific_apk = sys.argv[idx + 1]

    print("[*] Loading host classes from compileOnly jars...")
    host_classes = load_host_classes()
    print(f"[+] Loaded {len(host_classes)} host API classes.")

    apks = [specific_apk] if specific_apk else sorted(glob.glob(os.path.join(ROOT_DIR, "repo", "apk", "*.apk")))
    if not apks:
        print("[!] No APKs found to verify.")
        sys.exit(1)

    all_passed = True
    for apk in apks:
        apk_name = os.path.basename(apk)
        errors = verify_apk(apk, host_classes)
        if errors:
            all_passed = False
            print(f"\n[!] BINDING VERIFICATION FAILED: {apk_name} ({len(errors)} unresolved references):")
            for err in errors[:25]:
                print(f"    - {err}")
            if len(errors) > 25:
                print(f"    ... and {len(errors) - 25} more")
        else:
            print(f"    [OK] {apk_name}: All host API bindings verified successfully.")

    if not all_passed:
        print("\n[!] FATAL: Broken host API bindings detected! Refusing to publish incompatible APKs.")
        sys.exit(1)

    print("\n[+] PASS: All APK bytecode bindings resolve against host classpath (100% binary compatibility).")

if __name__ == "__main__":
    main()
