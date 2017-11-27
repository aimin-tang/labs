class Solution:
    def searchInsert(self, nums, target):
        if len(nums) == 0:
            return 0

        for i, n in enumerate(nums):
            if n < target:
                continue
            elif n >= target:
                return i
        else:
            return len(nums)


assert Solution().searchInsert([1,3,5,6], 5) == 2
assert Solution().searchInsert([1,3,5,6], 2) == 1
assert Solution().searchInsert([1,3,5,6], 7) == 4
assert Solution().searchInsert([1,3,5,6], 0) == 0
