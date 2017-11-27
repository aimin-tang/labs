class Solution:
    def removeElement(self, nums, val):
        return len([n for n in nums if n != val])

assert Solution().removeElement([3, 2, 2, 3], 3) == 2
