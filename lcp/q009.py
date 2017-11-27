class Solution:
    def isPalindrome(self, x):
        if x < 0:
            negative = True
            x = -x
        else:
            negative = False

        x_in_str = str(x)
        if x_in_str == x_in_str[::-1]:
            return True
        return False

assert Solution().isPalindrome(545) == True
assert Solution().isPalindrome(-545) == True
assert Solution().isPalindrome(-5456) == False
