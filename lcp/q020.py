class Solution:
    def isValid(self, s):
        lefts = ['(', '[', '{']
        rights = [')', ']', '}']

        check_list = []
        for c in s:
            if c in lefts:
                check_list.append(c)
            elif c in rights:
                if len(check_list) == 0:
                    return False
                elif lefts.index(check_list[-1]) == rights.index(c):
                    check_list.pop()
                else:
                    return False
            else:
                continue

        if len(check_list) != 0:
            return False
        else:
            return True

assert Solution().isValid('()') == True
assert Solution().isValid('()[]{}') == True
assert Solution().isValid('(]') == False
assert Solution().isValid('([)]') == False
