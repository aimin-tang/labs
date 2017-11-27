class Solution:
    def countAndSay(self, n):
        line = '1'
        if n == 1:
            return "1"
        # print("{}: {}".format(1, line))
        for i in range(2, n + 1):
            line = self.addNewLine(line)
            # print("{}: {}".format(i, line))
        return line

    def addNewLine(self, line):
        """
        Given '111221', return '312211'

        :param line:
        :return:
        """

        r = ""

        undone_pos = 0
        while undone_pos < len(line):
            next_num = line[undone_pos]
            c = 1
            checked_pos = undone_pos + 1
            while checked_pos < len(line):
                if line[checked_pos] == next_num:
                    c += 1
                    checked_pos += 1
                else:
                    break
            r += str(c)
            r += str(next_num)
            undone_pos += c

        return r

Solution().countAndSay(7)
