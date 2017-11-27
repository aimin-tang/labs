class Solution:
    def romanToInt(self, s):
        roman_letters = {
            'I': 1,
            'V': 5,
            'X': 10,
            'L': 50,
            'C': 100,
            'D': 500,
            'M': 1000
        }

        total = 0
        i = 0
        while i < len(s):
            curr_num = roman_letters[s[i]]
            if i < len(s) - 1:
                next_num = roman_letters[s[i+1]]
                if curr_num < next_num:
                    total -= curr_num
                else:
                    total += curr_num
            else:
                total += curr_num

            i += 1

        return total

assert Solution().romanToInt("IV") == 4