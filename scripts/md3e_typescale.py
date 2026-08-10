#!/usr/bin/env python3
"""把布局里硬编码的 textSize/textStyle 迁移到 res/values/styles.xml 的 Expressive 字阶。

用法: python scripts/md3e_typescale.py res/layout/xxx.xml [...]

试点已覆盖 activity_launcher / activity_settings；其余布局迁移时复用本脚本，
跑完务必人工复查一遍（脚本只做机械映射，不判断语义层级）。
"""
import re
import sys

APPEARANCE = 'android:textAppearance="@style/{}"'


def sub(text, pattern, style, flags=0):
    return re.sub(pattern, APPEARANCE.format(style), text, flags=flags)


def convert(text):
    # 1) 分区标题: allCaps + 12sp + bold -> Title Small Emphasized（Expressive 不用全大写）
    text = sub(
        text,
        r'android:textAllCaps="true"\s*\n\s*android:textSize="12sp"\s*\n\s*android:textStyle="bold"',
        "Expressive.Title.Small.Emphasized",
    )
    text = sub(
        text,
        r'android:textSize="12sp"\s*\n\s*android:textStyle="bold"\s*\n(\s*)android:textAllCaps="true"',
        "Expressive.Title.Small.Emphasized",
    )
    # 2) App bar 标题 22sp bold -> Headline Small Emphasized
    text = sub(
        text,
        r'android:textStyle="bold"\s*\n\s*android:textSize="22sp"',
        "Expressive.Headline.Small.Emphasized",
    )
    text = sub(
        text,
        r'android:textSize="22sp"\s*\n\s*android:textStyle="bold"',
        "Expressive.Headline.Small.Emphasized",
    )
    # 3) 条目主标题 16sp bold -> Title Medium Emphasized（两种属性顺序 + 单行紧凑写法）
    text = sub(
        text,
        r'android:textStyle="bold"\s+android:textSize="16sp"',
        "Expressive.Title.Medium.Emphasized",
    )
    text = sub(
        text,
        r'android:textSize="16sp"\s+android:textStyle="bold"',
        "Expressive.Title.Medium.Emphasized",
    )
    # 4) 次级标签 14sp bold -> Title Small Emphasized
    text = sub(
        text,
        r'android:textStyle="bold"\s+android:textSize="14sp"',
        "Expressive.Title.Small.Emphasized",
    )
    text = sub(
        text,
        r'android:textSize="14sp"\s+android:textStyle="bold"',
        "Expressive.Title.Small.Emphasized",
    )
    # 5) 剩余散装字号 -> 对应字阶
    text = sub(text, r'android:textSize="12sp"', "Expressive.Body.Small")
    text = sub(text, r'android:textSize="13sp"', "Expressive.Body.Medium")
    text = sub(text, r'android:textSize="14sp"', "Expressive.Body.Medium")
    text = sub(text, r'android:textSize="15sp"', "Expressive.Title.Medium")
    text = sub(text, r'android:textSize="16sp"', "Expressive.Body.Large")
    # 6) Expressive 留白：卡片内边距与卡片间距整体放大
    text = text.replace('android:padding="16dp"', 'android:padding="20dp"')
    text = text.replace('android:layout_marginBottom="12dp"', 'android:layout_marginBottom="14dp"')
    return text


def main(paths):
    for path in paths:
        with open(path, "r", encoding="utf-8") as fh:
            original = fh.read()
        converted = convert(original)
        if converted != original:
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(converted)
            print("updated", path)
        else:
            print("unchanged", path)


if __name__ == "__main__":
    main(sys.argv[1:])
