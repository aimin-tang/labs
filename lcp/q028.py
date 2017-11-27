class Solution:
    def strStr(self, haystack, needle):
        return haystack.find(needle)

assert Solution().strStr('hello', 'll') == 2
assert Solution().strStr('aaaaa', 'bba') == -1
