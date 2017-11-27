class Solution:
    def twoSum(self, nums, target):
        """
        :type nums: List[int]
        :type target: int
        :rtype: List[int]
        """

        indexed_nums = [(v, i) for i, v in enumerate(nums)]
        indexed_nums.sort()
        begin, end = 0, len(nums) - 1

        while begin < end:
            r = indexed_nums[begin][0] + indexed_nums[end][0]
            if r == target:
                return [indexed_nums[begin][1], indexed_nums[end][1]]
            elif r < target:
                begin += 1
            else:
                end -= 1
        else:
            raise RuntimeError("Can not find a solution.")


nums = [2, 7, 11, 15]
target = 9
s = Solution()
r = s.twoSum(nums, target)
assert r == [0, 1]

nums = [11, 15, 2, 7]
target = 9
s = Solution()
r = s.twoSum(nums, target)
assert r == [2, 3]
