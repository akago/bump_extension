import json

# 输入和输出文件路径
input_file = "found_repositories.json"
output_file_a = "filtered_2023_or_null.json"
output_file_b = "filtered_other.json"

# 读取JSON文件
with open(input_file, "r", encoding="utf-8") as f:
    data = json.load(f)

# 存储两个分类
file_a = {}  # lastCheckedAt is null or starts with 2023
file_b = {}  # everything else

for key, value in data.items():
    last_checked = value.get("lastCheckedAt")
    if last_checked is None or (isinstance(last_checked, str) and last_checked.startswith("2023")):
        file_a[key] = value
    else:
        file_b[key] = value

# 写入结果到两个文件
with open(output_file_a, "w", encoding="utf-8") as f:
    json.dump(file_a, f, indent=2, ensure_ascii=False)

with open(output_file_b, "w", encoding="utf-8") as f:
    json.dump(file_b, f, indent=2, ensure_ascii=False)

print(f"已完成分类：\n  - 条件匹配项写入 {output_file_a}\n  - 其余项写入 {output_file_b}")