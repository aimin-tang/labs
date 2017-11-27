class Solution:
    def mergeTwoLists(self, l1, l2):
        cur_node = l1
        while cur_node.next:
            cur_node = cur_node.next
        cur_node.next = l2

        return l1

assert Solution().mergeTwoLists([2], [1]) == [1, 2]