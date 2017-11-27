class Solution:
    def removeDuplicates(self, nums):
        if len(nums) == 0:
            return 0

        written = 1
        current = 1

        while current < len(nums):
            if nums[current] == nums[written - 1]:
                current += 1
            else:
                nums[written] = nums[current]
                written += 1
                current += 1

        return written

assert Solution().removeDuplicates([1, 1, 2]) == 2
assert Solution().removeDuplicates([]) == 0

