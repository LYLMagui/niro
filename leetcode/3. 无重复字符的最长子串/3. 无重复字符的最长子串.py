from typing import List


class Solution:
    def lengthOfLongestSubstring(self, s: str) -> int:
        """
        无重复字符的最长子串

        给定一个字符串 s，请你找出其中不含有重复字符的最长长度。

        :param s: 字符串
        :return: 无重复字符的最长子串长度
        """
        # 字典：记录每个字符最后一次出现的位置
        # 例如：{'a': 0, 'b': 1} 表示 'a' 最后出现在索引 0，'b' 在索引 1
        char_index = {}

        # 左指针：窗口的左边界
        left = 0

        # 记录最大长度
        result = 0

        # 右指针：遍历字符串
        for right in range(len(s)):
            # 获取当前字符
            current_char = s[right]

            # 判断当前字符是否在当前窗口 [left, right-1] 内
            # 条件1: current_char in char_index → 字符之前出现过
            # 条件2: char_index[current_char] >= left → 出现的位置在窗口内
            if current_char in char_index and char_index[current_char] >= left:
                # 遇到重复字符！
                # left 跳到重复字符位置的右边，抛弃重复字符及其左边的所有字符
                # 例如："abca" 当遇到第二个 'a' 时，'a' 在位置 0
                # left 从 0 跳到 0+1=1，新窗口变成 "bca"
                left = char_index[current_char] + 1

            # 更新字符的最后出现位置为当前位置
            char_index[current_char] = right

            # 更新最大长度
            # 当前窗口是 [left, right]，长度为 right - left + 1
            result = max(result, right - left + 1)

        return result


# ==================== 测试用例 ====================


def run_tests():
    """运行所有测试用例"""
    solution = Solution()

    test_cases = [
        # (s, expected)
        ("abcabcbb", 3),  # 示例1: "abc"
        ("bbbbb", 1),  # 示例2: "b"
        ("pwwkew", 3),  # 示例3: "wke"
        ("", 0),  # 空字符串
        ("a", 1),  # 单字符
        ("au", 2),  # 两个不同字符
        ("dvdf", 3),  # "vdf"
        ("abba", 2),  # "ab" 或 "ba"
    ]

    for i, (s, expected) in enumerate(test_cases, 1):
        result = solution.lengthOfLongestSubstring(s)
        status = "✓" if result == expected else "✗"
        print(f'用例{i}: s="{s}"')
        print(f"  期望: {expected}, 实际: {result} {status}\n")


if __name__ == "__main__":
    run_tests()
