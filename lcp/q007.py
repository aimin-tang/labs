class Solution:
    def reverse(self, x):
        if x == 0:
            return 0
        elif x < 0:
            negative = True
            x = -1 * x
        else:
            negative = False

        s = str(x)
        r = "".join(reversed(s))
        if negative:
            r = "-" + r
        r = int(r)

        if r > 2 ** 31 - 1 or r < -1 * 2 ** 31:
            return 0

        return r

assert Solution().reverse(123) == 321
assert Solution().reverse(-123) == -321
assert Solution().reverse(120) == 21
