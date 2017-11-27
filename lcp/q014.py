class Solution:
    def longestCommonPrefix(self, strs):
        prefix = ""

        if len(strs) == 0:
            return ""
        else:
            strs.sort(key=len)
            shortest = strs[0]

        pos = 0
        done = False
        while not done:
            if pos == len(shortest):
                done = True
                continue
            char_pos = shortest[pos]
            for str in strs:
                if str[pos] != char_pos:
                    print("prefix last: {}".format(prefix))
                    done = True
                    break
            else:
                prefix += char_pos
                print("prefix: {}".format(prefix))
                pos += 1

        return prefix

assert Solution().longestCommonPrefix(['abc', 'abcd', 'abcde']) == 'abc'
assert Solution().longestCommonPrefix(['aa', 'a']) == 'a'
assert Solution().longestCommonPrefix(['a', 'b']) == ''
