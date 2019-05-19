package org.fog.application;

import java.util.HashMap;
import java.util.Map;

public class Fuck {
    private static int lengthOfLIS(int[] nums) {
        int n = nums.length;
        if (n == 0) {
            return 0;
        }
        int[] dp = new int[n];
        int res = 0;
        for (int i = 0; i < n; i++) {
            dp[i] = 1;//子序列就是本身
            for (int j = 0; j < i; j++) {
                if (nums[i] > nums[j] && dp[j] + 1 > dp[i]) {
                    dp[i] = dp[j] + 1;//以A[j]结尾的最长上升子序列的长度，加上A[i]
                }
            }
            res = Math.max(res, dp[i]);
        }
        return res;
    }

    public static void main(String[] args) {
        int[] nums = {10, 9, 2, 5, 3, 7, 101, 18};
        int num = lengthOfLIS(nums);
        System.out.println(num);
//        Map<String, Integer> book = new HashMap<>();
//        book.put("fuck", 13);
    }
}
