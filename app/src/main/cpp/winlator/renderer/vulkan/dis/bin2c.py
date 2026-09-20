#!/usr/bin/env python3
"""Embed a SPIR-V binary into a C header as a uint32_t array plus its byte size.

CorkOS needs this for the DIS compute shaders: vkr_dis.c references each shader as
``<name>`` (the word array) and ``<name>_size`` (its size in bytes, passed straight to
VkShaderModuleCreateInfo::codeSize). glslangValidator's ``-x`` flag emits only the array,
so the size symbol is generated here.

Usage: bin2c.py <input.spv> <output.h> <var_name>
"""
import sys


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: bin2c.py <input.spv> <output.h> <var_name>", file=sys.stderr)
        return 2

    src, dst, name = sys.argv[1], sys.argv[2], sys.argv[3]
    with open(src, "rb") as fh:
        data = fh.read()

    if len(data) % 4 != 0:
        print(f"{src}: size {len(data)} is not a multiple of 4", file=sys.stderr)
        return 1

    words_per_line = 8
    lines = []
    for off in range(0, len(data), 4 * words_per_line):
        chunk = data[off:off + 4 * words_per_line]
        words = []
        for i in range(0, len(chunk), 4):
            w = int.from_bytes(chunk[i:i + 4], "little")
            words.append("0x%08x" % w)
        lines.append("    " + ",".join(words))

    with open(dst, "w", encoding="utf-8") as fh:
        fh.write("/* Generated from %s -- do not edit. */\n" % src.rsplit("/", 1)[-1])
        fh.write("#pragma once\n\n")
        fh.write("#include <stdint.h>\n\n")
        fh.write("const uint32_t %s[] = {\n%s\n};\n\n" % (name, ",\n".join(lines)))
        fh.write("const size_t %s_size = %d;\n" % (name, len(data)))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
