from turtle import left
from typing import List


class Solution:
    def minSubArrayLen(self, target: int, nums: List[int]) -> int:
        """
        长度最小的子数组

        给定一个含有 n 个正整数的数组 nums 和一个正整数 target。
        找出数组中满足其和 >= target 的长度最小的连续子数组，并返回其长度。
        如果不存在符合条件的子数组，返回 0。

        :param target: 正整数 target
        :param nums: 正整数数组
        :return: 满足条件的最小子数组长度，不存在则返回 0
        """
        left = 0
        sum = 0
        min_len = float('inf')

        for right in range(len(nums)):
            sum += nums[right]

            
            while sum >= target:
                # 当满足条件时先记录当前的长度
                min_len = min(min_len,right - left + 1)
                sum -= nums[left]
                left += 1
            return min_len if min_len != float('inf') else 0

# ==================== 测试用例 ====================

def run_tests():
    """运行所有测试用例"""
    solution = Solution()

    test_cases = [
        # (target, nums, expected)
        (7, [2, 3, 1, 2, 4, 3], 2),    # 示例1
        (4, [1, 4, 4], 1),              # 示例2
        (11, [1, 1, 1, 1, 1, 1, 1, 1], 0),  # 示例3
        (15, [1, 2, 3, 4, 5], 5),      # 需要全部元素
        (5, [2, 1, 3], 1),             # 单个元素满足
        (100, [1, 2, 3], 0),           # 不存在
    ]

    for i, (target, nums, expected) in enumerate(test_cases, 1):
        result = solution.minSubArrayLen(target, nums)
        status = "✓" if result == expected else "✗"
        print(f"用例{i}: target={target}, nums={nums}")
        print(f"  期望: {expected}, 实际: {result} {status}\n")


if __name__ == "__main__":
    run_tests()
