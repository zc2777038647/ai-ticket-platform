# S4 Algorithm Reasoning Guide

> 面向 Java 后端 / AI 应用后端实习的算法推理教材。
>
> 目标不是背 Hot 100 的答案，而是面对陌生题时，能够从问题、约束和不变量推出算法模型，再用 Java 写出清晰、可验证的代码。

---

# 0. 如何使用这份教材

算法学习的固定顺序是：

```text
题目
↓
问题建模
↓
约束
↓
暴力方案
↓
重复计算 / 瓶颈
↓
寻找结构
↓
建立不变量
↓
推出算法模型
↓
证明正确性
↓
复杂度
↓
Java 实现
↓
反例
↓
变形与迁移
```

不要先看代码。每个母题按照以下顺序学习：

1. 只读 Problem，自己重述输入、输出和边界。
2. 写出 O(n²) 或更直接的暴力方案。
3. 找出暴力重复做了什么。
4. 写出 `INVARIANT`，再选择数据结构或模型。
5. 只看 Hint 1～4，尽量自己完成。
6. 看推导和 Java 实现，逐行说明每个变量为什么存在。
7. 手动构造正常例子、边界例子、反例。
8. 改一个条件，判断原算法是否还成立。

## 学习等级

| 等级 | 标准 |
|---|---|
| L1 | 看懂题目和标准实现 |
| L2 | 能解释模型、不变量、复杂度 |
| L3 | 不看答案能从暴力推导并手写 |
| L4 | 条件变化后仍能判断模型是否成立并迁移 |

Java 后端实习的核心模型应达到 L3；Hash、滑动窗口、前缀和、二分、链表、树、BFS/DFS、堆、回溯、DP 和并发业务中的条件更新应尽量达到 L4。

---

# 1. 算法问题解决总框架

## 1.1 第一步：把题目翻译成状态

先回答：

```text
数据是什么？
目标是什么？
允许哪些操作？
输出一个值、一个位置、一个集合还是一条路径？
数据是否有序？是否连续？是否可以重复使用？
```

例如“找和为 K 的连续子数组”不是普通“两数之和”：关键字是**连续**、目标是**和**，并且数组可能含负数。这个条件决定了滑动窗口是否具有单调性。

## 1.2 第二步：先写暴力

暴力不是浪费时间，而是定位瓶颈：

```text
枚举所有候选
→ 检查每个候选
→ 统计总工作量
```

如果暴力是 O(n²)，要问第二层循环每次是否在重复计算同一段、同一前缀、同一状态或同一最小值。

## 1.3 第三步：寻找“已知信息”

常见优化来源：

| 重复工作 | 可以保存的东西 | 常见模型 |
|---|---|---|
| 过去是否出现过 | 值到位置/次数 | HashMap/HashSet |
| 区间总和反复计算 | 前缀结果 | Prefix Sum |
| 连续窗口反复枚举 | 窗口状态 | Sliding Window |
| 每次找当前最小/最大 | 动态优先级 | Heap/Deque |
| 相同子问题重复递归 | 状态结果 | DP/Memo |
| 无权图按距离扩展 | 层级队列 | BFS |
| 选择后撤销 | 决策路径 | Backtracking |

## 1.4 第四步：写不变量

`INVARIANT` 是循环或递归每一步都必须保持的事实。没有不变量，代码通常只能靠样例“碰巧通过”。

## 1.5 第五步：证明与复杂度

至少用一种方式解释正确性：

- 循环不变量：每轮开始时某个事实成立。
- 归纳：规模为 n 正确，规模 n+1 由前一步推出。
- 单调性：答案空间的真假排列不会反复变化。
- 状态定义：`dp[i]` 的含义和转移覆盖所有合法选择。
- 反证：假设算法漏解或错选，推导矛盾。

---

# 2. Complexity Reasoning

## 2.1 不背复杂度，数工作量

### O(1)

操作次数与输入规模无关，例如数组按已知下标访问。注意 HashMap 的 O(1) 是平均分析，不是数学上任何情况下绝对 O(1)。

### O(log n)

每次把问题规模缩小一半或按树高移动，如二分查找。

### O(n)

每个元素被常数次处理。滑动窗口中 `right` 每个元素加入一次，`left` 最多移除一次，总操作 2n 级别。

### O(n log n)

排序通常是 n 个元素经过 log n 层比较；“排序 + 一次扫描”仍是 O(n log n)。

### O(n²)

两层都随 n 增长的独立枚举；如果内层指针不会回退，嵌套写法也可能是 O(n)，不能只看缩进。

### O(2^n)、O(n!)

每个元素选/不选形成决策树；排列每层可选数量递减。回溯的复杂度要结合剪枝和输出规模解释。

## 2.2 空间复杂度的边界

区分：

```text
输入数组是否允许原地修改？
递归栈是否算额外空间？
输出结果是否计入空间？
```

面试时说明口径比机械报一个数字更重要。

## 2.3 常见复杂度陷阱

- `String +=` 在循环里可能产生大量临时对象，应考虑 `StringBuilder`。
- `ArrayList.remove(0)` 是 O(n)，队列应使用 `ArrayDeque`。
- 递归树看似两层循环，但若两个指针只向前移动，可能是 O(n)。
- HashSet 平均 O(1)，极端碰撞、扩容和装箱成本不能完全忽略。
- `PriorityQueue` 插入/删除堆顶 O(log k)，查看堆顶 O(1)。

---

# 3. Hash：把“过去的信息”保存起来

## 3.1 问题原型

适用于：

```text
我现在看到 x，想快速知道过去是否见过与 x 有关系的 y。
```

它不依赖连续性，也不要求数组有序。

## 3.2 Necessary Conditions

- 需要快速判断存在性、次数或对应位置。
- 可以用额外空间保存过去信息。
- 关系可以由 key 直接定位，而不是必须按顺序搜索。

不满足时不要机械用 Hash：如果目标是保持顺序、求最小/最大或需要区间结构，可能应使用排序、堆或前缀结构。

## 3.3 INVARIANT

扫描到当前位置 `i` 时，Hash 结构准确保存了 `0..i-1` 的必要信息。

## 3.4 母题：Two Sum（LeetCode 1）

### Problem

给定整数数组和 target，返回两个不同位置，使两数之和等于 target。这里只保留简短概述，不复制完整题面。

### Hint 0

先枚举所有下标对。

### Hint 1

当看到 `x` 时，另一个数是多少？

### Hint 2

如果已经扫描过的数保存了位置，就不必重新搜索。

### Hint 3

`map` 在当前元素处理前只包含之前元素，避免使用同一个位置两次。

### Hint 4

考虑 `HashMap<Integer, Integer>`。

<details>
<summary>查看推导与 Java 实现</summary>

暴力枚举 O(n²)：对每个 i 再扫 j。重复工作是“寻找 complement”被反复线性扫描。

扫描 `x` 时，所需值为 `target - x`。若它已经在 map 中，直接得到答案；否则把 x 和位置存入 map。

```java
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> seen = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int need = target - nums[i];
        if (seen.containsKey(need)) {
            return new int[]{seen.get(need), i};
        }
        seen.put(nums[i], i);
    }
    return new int[0];
}
```

正确性：处理 i 时 map 只保存前面下标；若存在合法前项，它已在 map 中，否则当前值加入后供未来使用。时间 O(n) 平均，空间 O(n)。

</details>

### 反例与变形

- 若要求返回所有不重复组合，要处理重复值和排序，不能直接复用第一个答案。
- 若数组有序，可用双指针，Hash 不是唯一选择。
- 若需要连续子数组和为 K，转为前缀和 + Hash，而不是 Two Sum 直接套在原数组上。

## 3.5 相关母题

### Group Anagrams（49）

核心是为每个单词构造稳定 key：排序字符串或 26 计数。`INVARIANT`：相同 key 的词必属于同组。若字符集扩大，固定 26 计数不再成立，要改变 key 设计。

### Longest Consecutive Sequence（128）

把所有数放入 Set，只从 `x-1` 不存在的起点向后扩展。`INVARIANT`：每个连续段只从起点遍历一次，平均 O(n)。若每个数都重复向前找，就退化 O(n²)。

### Subarray Sum Equals K（560）

前缀和 `prefix[j] - prefix[i] = k`，所以当前 prefix 需要查 `prefix-k` 出现次数。数组含负数时普通滑动窗口失去单调性，这正是 Prefix Sum + Hash 的必要条件。

## 3.6 Java 注意事项

- `HashMap` key 必须正确实现 `equals/hashCode`。
- `get` 返回 null 可能与 value 为 null 混淆，计数通常用 `getOrDefault`。
- 计数和前缀和可能溢出，优先考虑 `long`。
- 题目只需要存在性时用 `HashSet`，不必保存多余位置。

## 3.7 Self Check

1. 当前 map 到底保存了哪些元素？
2. 为什么不能先 put 当前元素再查？
3. 如果需要所有答案，重复如何处理？
4. 什么时候 Hash 的平均 O(1) 不等于最坏 O(1)？

---

# 4. Two Pointers：用两个边界表达关系

## 4.1 问题原型

两个指针不是一种固定题型，而是一种减少重复扫描的方式。常见条件：

- 数组有序，左右移动会产生可预测变化；
- 研究一个区间/两端关系；
- 两个指针只向前或相向移动。

## 4.2 INVARIANT

每次移动指针后，已经排除的区域不可能包含更优/更合法答案，或当前区间仍代表所有未排除候选。

## 4.3 母题：Container With Most Water（11）

### 暴力

枚举所有左右边界 O(n²)。

### 推导

面积为 `min(height[left], height[right]) * (right-left)`。若左边更短，移动右边只会减少宽度，短板仍是左边，无法得到更大面积；必须移动短板一侧，才有机会提高高度。

<details>
<summary>查看 Java 实现</summary>

```java
public int maxArea(int[] height) {
    int left = 0, right = height.length - 1;
    int best = 0;
    while (left < right) {
        int width = right - left;
        best = Math.max(best, Math.min(height[left], height[right]) * width);
        if (height[left] <= height[right]) {
            left++;
        } else {
            right--;
        }
    }
    return best;
}
```

每个指针最多移动 n 次，时间 O(n)，额外空间 O(1)。

</details>

## 4.4 相关题

- 3Sum（15）：排序 + 固定一个数 + 双指针；要处理重复跳过。
- Trapping Rain Water（42）：双指针维护左右最高边界；移动较低边界，因为低边界决定当前可接水量。
- Remove Duplicates from Sorted Array（26）：慢指针保存结果区，快指针扫描输入。
- Move Zeroes（283）：慢指针维护非零元素写入位置。

## 4.5 Failure Conditions

- 无序且移动指针没有单调依据时，不能硬套相向指针。
- 需要所有组合且没有排序/去重策略时，指针可能漏解或重复。
- 区间和含负数且没有单调性时，普通窗口式双指针失效。

## 4.6 Self Check

为什么移动的是短板？如果移动长板，哪些候选已经被排除？双指针的正确性依赖哪种单调关系？

---

# 5. Sliding Window：连续区间的增量维护

## 5.1 问题原型

研究对象必须是**连续子数组或子串**，并且从窗口 `[left, right]` 移动一格时，窗口状态可以增量加入/删除。

## 5.2 Necessary Conditions

- 目标是连续区间。
- 窗口状态可用 O(1) 或均摊 O(1) 更新。
- 窗口非法后，移动某一侧能够恢复合法，通常依赖单调性。

如果数组有负数导致“右边扩大不一定让和变大”，普通 `sum <= k` 窗口就失去单调性。

## 5.3 INVARIANT

循环每次维护：

```text
当前窗口 [left, right] 始终满足题目合法条件，
或正在通过移动 left 恢复合法。
```

## 5.4 母题：Longest Substring Without Repeating Characters（3）

### 暴力

枚举所有子串并检查重复，O(n³)；用 Set 检查每个起点可降到 O(n²)。

### 瓶颈

重复检查窗口内已有字符。

### 推导

右指针加入字符；若窗口出现重复，移动左指针并删除离开的字符，直到合法。

<details>
<summary>查看 Java 实现</summary>

```java
public int lengthOfLongestSubstring(String s) {
    Set<Character> window = new HashSet<>();
    int left = 0, best = 0;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        while (window.contains(c)) {
            window.remove(s.charAt(left++));
        }
        window.add(c);
        best = Math.max(best, right - left + 1);
    }
    return best;
}
```

`right` 每个字符加入一次，`left` 每个字符最多删除一次，总操作 O(n)，空间 O(min(n, 字符集大小))。

</details>

## 5.5 相关题与识别

- Minimum Window Substring（76）：窗口满足覆盖需求时收缩，维护欠缺计数。
- Permutation in String（567）：固定长度窗口比较字符频次。
- Find All Anagrams in a String（438）：固定长度频次窗口。
- Maximum Number of Vowels in a Substring of Given Length（1456）：固定窗口增量计数。
- Longest Repeating Character Replacement（424）：窗口长度 - 窗口最高频 <= k。

## 5.6 为什么“固定窗口”和“可变窗口”不同？

固定窗口的长度由题目给定，right 增加一个就 left 移除一个；可变窗口需要根据合法性收缩。不要把 `while` 收缩逻辑机械放进所有窗口题。

## 5.7 Failure Cases

`Subarray Sum Equals K` 含负数时，右移不保证 sum 单调，不能直接用“超过 K 就左移”。应改为 Prefix Sum + Hash。

## 5.8 Self Check

1. 窗口状态是什么？
2. 什么时候移动 right？
3. 什么时候必须移动 left？
4. left/right 是否会回退？
5. 为什么总操作仍是 O(n)？

---

# 6. Prefix Sum：把区间问题变成两个前缀的关系

## 6.1 问题原型

多次询问连续区间聚合值，且聚合满足“前缀可相减/组合”：

```text
rangeSum(i, j) = prefix[j + 1] - prefix[i]
```

## 6.2 INVARIANT

扫描到位置 i 时，`prefix` 准确表示从起点到 i 的累计结果。

## 6.3 母题：Subarray Sum Equals K（560）

若 `prefix[j] - prefix[i] = k`，则在当前 prefix 出现前查 `prefix-k` 的次数即可。Hash 保存的是过去前缀出现次数。

```java
public int subarraySum(int[] nums, int k) {
    Map<Integer, Integer> count = new HashMap<>();
    count.put(0, 1);
    int prefix = 0, answer = 0;
    for (int value : nums) {
        prefix += value;
        answer += count.getOrDefault(prefix - k, 0);
        count.merge(prefix, 1, Integer::sum);
    }
    return answer;
}
```

`count.put(0, 1)` 代表从数组开头正好得到 k 的情况；这是最容易漏掉的边界。

## 6.4 相关题

- Product of Array Except Self（238）：左右前缀/后缀乘积，不使用除法。
- Find Pivot Index（724）：总和与左前缀关系。
- Continuous Subarray Sum（523）：前缀和模 k 的重复位置。
- Range Sum Query - Immutable（303）：前缀数组。

## 6.5 失效条件

如果操作不是可组合的加法/可逆关系，前缀相减不一定成立；例如一般的区间最大值需要 Sparse Table/Segment Tree 等其他结构，不能直接相减。

---

# 7. Binary Search：真正的核心是单调 Predicate

## 7.1 问题原型

不是“看到数组就二分”，而是存在一个搜索空间和单调判断：

```text
false false false true true true
```

要找第一个 true 或最后一个 false。

## 7.2 Necessary Conditions

- 搜索空间有可比较顺序。
- `check(x)` 具有单调性。
- 被排除的一半不可能包含答案。

## 7.3 INVARIANT

答案始终存在于当前 `[left, right]` 搜索空间（或在边界题中，`answer` 不超过某个闭区间）。

## 7.4 母题：Binary Search（704）

```java
public int search(int[] nums, int target) {
    int left = 0, right = nums.length - 1;
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (nums[mid] == target) return mid;
        if (nums[mid] < target) left = mid + 1;
        else right = mid - 1;
    }
    return -1;
}
```

不要写 `(left + right) / 2` 而忽略 int 溢出风险。

## 7.5 Search in Rotated Sorted Array（33）

至少有一半保持有序；判断 target 是否位于有序半区，再决定排除另一半。重复元素版本会破坏“哪半一定有序”的强判断，需要额外收缩边界。

## 7.6 Binary Search on Answer

### 例：Koko Eating Bananas（875）

猜速度 x，判断在 x 下能否按时吃完。速度越大，所需时间不增加，因此 `canFinish(x)` 单调。搜索最小可行速度，而不是在数组中搜值。

## 7.7 Failure Cases

没有单调 predicate 时，`false/true` 可能来回变化，二分会错误丢弃答案。先证明单调性，再写模板。

---

# 8. Linked List：指针不变量

## 8.1 总原则

修改 `next` 前先保存后继：

```java
ListNode next = current.next;
current.next = previous;
previous = current;
current = next;
```

否则可能丢失剩余链表。

## 8.2 母题：Reverse Linked List（206）

### INVARIANT

循环开始时，`previous` 是已经反转好的前缀，`current` 是尚未处理的第一个节点。

<details>
<summary>查看 Java 实现</summary>

```java
public ListNode reverseList(ListNode head) {
    ListNode previous = null;
    ListNode current = head;
    while (current != null) {
        ListNode next = current.next;
        current.next = previous;
        previous = current;
        current = next;
    }
    return previous;
}
```

时间 O(n)，额外空间 O(1)。

</details>

## 8.3 Fast/Slow

- Middle of the Linked List（876）：fast 两步、slow 一步。
- Linked List Cycle（141）：若有环，快慢指针会在有限环内相遇。
- Linked List Cycle II（142）：相遇后一个指针回头，步长关系推出入口。

## 8.4 Merge 与相交

- Merge Two Sorted Lists（21）：哨兵节点简化边界，当前选择最小头。
- Merge k Sorted Lists（23）：Heap 保留每条链的当前最小节点，或分治合并。
- Intersection of Two Linked Lists（160）：两个指针分别走 A+B 与 B+A，抵消长度差。

## 8.5 反例

空链表、单节点、两个节点、环入口就是头节点；先列边界再决定 while 条件。

---

# 9. Stack、Monotonic Stack、Queue/Deque

## 9.1 Stack：最近未匹配信息

### 母题：Valid Parentheses（20）

遇到左括号入栈；遇到右括号必须匹配栈顶。`INVARIANT`：栈保存尚未匹配且按嵌套顺序等待的左括号。

Java 用 `ArrayDeque<Character>`，不使用过时的 `Stack`。

## 9.2 Monotonic Stack

### 问题原型

对每个元素找右侧第一个更大/更小值。

### INVARIANT

栈内索引对应的值保持指定单调关系；被弹出的元素已经找到答案，或已经不可能成为未来答案。

### Daily Temperatures（739）

维护递减温度索引栈；新温度更高时不断弹出并计算距离。每个索引最多入栈/出栈一次，所以 O(n)。

### Largest Rectangle in Histogram（84）

单调递增栈维护可能作为高度起点的柱子；遇到更低高度时弹出并确定右边界。哨兵 0 使栈清空。

## 9.3 Queue/Deque

- BFS 需要先进先出：`ArrayDeque`。
- 滑动窗口最大值需要队头是最大值，队列索引递减；过期索引从队头移除。
- 不要用 `ArrayList.remove(0)` 模拟队列。

---

# 10. Heap / PriorityQueue：只维护最重要的一小部分

## 10.1 问题原型

不需要完整排序，只需要当前最小/最大、Top-K 或动态合并。

## 10.2 INVARIANT

堆顶始终是当前候选中最小/最大的元素；大小为 k 的堆只保留对最终答案有潜在贡献的 k 个元素。

## 10.3 Kth Largest Element（215）

维护大小 k 的小根堆：遍历元素，超过 k 就弹出最小，最后堆顶是第 k 大。时间 O(n log k)，空间 O(k)。

```java
public int findKthLargest(int[] nums, int k) {
    PriorityQueue<Integer> heap = new PriorityQueue<>();
    for (int value : nums) {
        heap.offer(value);
        if (heap.size() > k) heap.poll();
    }
    return heap.peek();
}
```

## 10.4 Top K Frequent Elements（347）

先计数，再用大小 k 的堆；或者桶排序。选择取决于值域、稳定性和实现清晰度。

## 10.5 Merge k Sorted Lists（23）

堆里只放每条链当前头；弹出最小后把它的 next 放入。堆大小 k，时间 O(N log k)。

## 10.6 Java 注意事项

`PriorityQueue` 默认是小根堆；大根堆写 `Comparator.reverseOrder()` 或显式比较。比较器必须避免减法溢出：不要随意写 `a - b`。

---

# 11. Binary Tree：递归定义自然推出遍历

## 11.1 递归四问

对每个节点先问：

1. 当前节点应该做什么？
2. 左子树返回什么？
3. 右子树返回什么？
4. 当前结果如何组合？

## 11.2 三种深度优先遍历

- Preorder：当前 → 左 → 右；适合复制/序列化结构。
- Inorder：左 → 当前 → 右；BST 中得到有序序列。
- Postorder：左 → 右 → 当前；适合子树结果先于父节点。

## 11.3 母题：Maximum Depth（104）

空树为 0，非空树为 `1 + max(leftDepth, rightDepth)`。这是递归定义，不是背公式。

## 11.4 Level Order（102）

BFS 队列按层处理；每轮记录当前 `queue.size()`，只处理这一层。`INVARIANT`：队列中保存下一批待扩展节点。

## 11.5 Diameter of Binary Tree（543）

后序返回子树高度，同时用 `leftHeight + rightHeight` 更新全局直径。返回值和答案不是同一个量，是树 DP 的典型。

## 11.6 Lowest Common Ancestor

递归返回“当前子树是否包含目标”的信息；若左右都返回非空，当前就是分叉点。先确认题目是普通二叉树还是 BST。

## 11.7 Validate BST（98）

不能只比较父子节点；要维护整棵子树合法的上下界，或利用中序遍历严格递增。

---

# 12. DFS / BFS：先理解搜索空间

## 12.1 DFS

DFS 优先深入一个分支，适合：连通块、路径存在性、递归结构、回溯状态。

`INVARIANT`：进入节点时记录访问状态；离开时按问题需要恢复或保留。

## 12.2 BFS

BFS 按层扩展。无权图每条边代价相同，因此第一次到达节点时就是最短边数距离。

### Word Ladder（127）

每次改变一个字符得到邻居，BFS 按变换步数扩展。关键瓶颈是邻居生成，是否预处理通配模式取决于规模。

### Rotting Oranges（994）

多源 BFS：所有初始腐烂橘子同时入队，层数代表分钟。不要从每个坏橘子分别 DFS，否则难以表达最短时间。

### Number of Islands（200）

发现陆地后 DFS/BFS 标记整个连通块，每个格子最多访问一次，O(mn)。

## 12.3 Failure Conditions

- 边权不相同，普通 BFS 不再保证最短，需要 Dijkstra/0-1 BFS 等。
- DFS 找到一条路径不代表最短路径。
- 图有环却不记录 visited，会无限循环。

---

# 13. Backtracking：决策树、约束、撤销

## 13.1 统一模型

```text
State
Choice
Constraint
Path
Undo
```

递归每层选择一个决策；进入前修改状态，返回后撤销。

## 13.2 母题：Subsets（78）

每个元素有“选/不选”两条边，形成二叉决策树，结果数 2^n。

```java
private void dfs(int[] nums, int index,
                 List<Integer> path, List<List<Integer>> result) {
    result.add(new ArrayList<>(path));
    for (int i = index; i < nums.length; i++) {
        path.add(nums[i]);
        dfs(nums, i + 1, path, result);
        path.remove(path.size() - 1);
    }
}
```

复制 path 很重要，否则结果列表中的每一项会共享同一个可变对象。

## 13.3 Permutations（46）

状态是当前路径和 used 数组；每层选择未使用元素。与 Subsets 不同，排列不保持 index 单调。

## 13.4 Combination Sum（39）

选择是否允许重复决定下一层 index 是否保持。先明确题目语义，再写循环边界。

## 13.5 N-Queens（51）

逐行放置；列、主对角线、副对角线是约束集合。剪枝是把“不可能成功”的分支提前截断。

## 13.6 Failure Conditions

如果答案数量巨大，任何算法都至少要付出输出规模；回溯复杂度不能只写 O(2^n) 而忽略复制结果和剪枝。

---

# 14. Graph 与 Union-Find

## 14.1 图问题先确定模型

```text
节点是什么？
边是什么？有向/无向？有权/无权？
是否需要最短路、连通性、拓扑顺序、环检测？
```

## 14.2 Union-Find：动态连通性

### Problem 原型

逐步加入无向边，判断两个节点是否属于同一连通分量。

### INVARIANT

每个集合有一个代表元；`find(x)` 返回的代表相同，当且仅当 x 与 y 当前连通。

### 推导

暴力每加一条边重新 DFS 会重复遍历；并查集用 parent 表示集合合并，路径压缩 + 按秩/大小合并使操作近似 O(α(n))。

## 14.3 典型题

- Number of Provinces（547）：合并连接城市。
- Redundant Connection（684）：边两端已在同集合时是冗余边。
- Accounts Merge（721）：共享邮箱的账户合并。

## 14.4 Topological Sort

课程表（207/210）是有向图环检测。入度为 0 的节点先处理；若最终处理数量不足，存在环。拓扑排序不是普通 BFS，而是以入度约束驱动的队列。

---

# 15. Greedy：局部选择必须有交换或边界证明

## 15.1 问题原型

每一步选择当前看起来最优，并且这个选择不会破坏全局最优。

## 15.2 Necessary Conditions

- 存在可证明的局部最优性质。
- 选择后剩余问题仍是同类问题。
- 能用交换论证、边界论证或 matroid-like 性质证明。

仅仅“看起来合理”不够。

## 15.3 Jump Game（55）

维护当前能到达的最远位置；扫描到的位置若超过 reach，则不可达。`INVARIANT`：扫描前缀内，reach 是所有可行路径能到达的最远位置。

## 15.4 Jump Game II（45）

把当前跳跃范围看作一层，扫描这一层能扩展出的 farthest；到达边界时跳数 +1。这是区间层级思想，不是随便选最大步长。

## 15.5 Gas Station（134）

若总油量不足则无解；从某点出发累计为负时，这个点以及之前的候选都不可能是起点，直接跳到下一点。证明依赖前缀和的最小点。

## 15.6 Partition Labels（763）

先记录每个字符最后出现位置；当前段右边界扩展到段内字符的最远最后位置。边界闭合时输出长度。

## 15.7 Failure Cases

Coin Change 之类问题中“每次选最大硬币”可能不是最优；没有交换证明时，考虑 DP 或搜索。

---

# 16. Dynamic Programming：先定义状态，再写公式

## 16.1 四问

1. `State`：`dp[i]` 中文到底代表什么？
2. `Choice`：当前位置有哪些选择？
3. `Transition`：选择后从哪个更小状态来？
4. `Base Case`：规模最小时答案是什么？

如果 `dp[i]` 说不清楚，禁止直接写转移式。

## 16.2 母题：Climbing Stairs（70）

`dp[i]` 表示到达第 i 阶的方法数；最后一步来自 i-1 或 i-2，因此 `dp[i] = dp[i-1] + dp[i-2]`。空间可压缩为两个变量，因为只依赖最近两项。

## 16.3 House Robber（198）

`dp[i]` 表示考虑前 i 个房屋的最大金额；选择偷 i 就不能偷 i-1，不偷 i 则沿用 i-1。不是“看到最大就选”，而是相邻约束下的状态。

## 16.4 Coin Change（322）

`dp[amount]` 表示凑出 amount 所需最少硬币数；枚举最后一枚硬币，非法状态用 `amount+1` 或 INF 表示。顺序和重复使用语义必须先确认。

## 16.5 Longest Increasing Subsequence（300）

O(n²) 状态：`dp[i]` 是以 i 结尾的 LIS 长度；转移看所有 j<i 且 nums[j]<nums[i]。O(n log n) 的 tails 数组不直接保存真实序列长度含义，要解释它维护的是每个长度的最小结尾。

## 16.6 Longest Common Subsequence（1143）

`dp[i][j]` 表示两个前缀的 LCS；末字符相等则 1+上左，不等则丢弃一侧取 max。二维状态的含义比公式更重要。

## 16.7 背包模型

0/1 背包每件物品最多一次；完全背包可重复。遍历容量的方向影响是否重复使用：这是循环顺序背后的不变量，不是模板口诀。

## 16.8 Interval DP 与 Tree DP

当状态是区间 `[l,r]` 或树节点返回多个信息时，仍回到 State/Choice/Transition/Base 四问。

## 16.9 DP Failure Conditions

- 状态丢失了未来决策所需信息，转移就不完整。
- 维度过少会把不同历史错误合并。
- 只背公式，遇到边界、路径恢复或滚动数组就容易错。

---

# 17. Intervals、Sorting、Top-K

## 17.1 Interval Problems

### Merge Intervals（56）

先按 start 排序；维护当前合并区间 `[start,end]`。下一个 start <= end 就扩展，否则关闭旧区间。排序后，局部相邻关系足以决定全局合并。

### Insert Interval（57）

按“完全在左、重叠、完全在右”三段处理，避免先合并所有区间再猜位置。

### Non-overlapping Intervals（435）

按 end 排序，优先保留结束早的区间；这是经典交换论证：更早结束给后续留下更多空间。

## 17.2 Sorting 的推导

- 需要全局顺序：排序是 O(n log n) 的基础工具。
- 需要稳定相邻关系：排序后双指针/贪心常成立。
- 只需要 Top-K：完整排序可能浪费，使用堆或 Quickselect。

## 17.3 Counting Sort / Bucket

值域小且可控时，计数桶可从 O(n log n) 降到 O(n+range)；值域巨大或稀疏时可能耗费不可接受空间。

---

# 18. Algorithm Model Recognition

看到场景先回答“它研究什么结构”，不要直接说算法名。

## 18.1 40 个识别场景

1. 两数和 target：过去是否有 complement？→ Hash。
2. 单词按字母组成分组：如何构造等价 key？→ Hash/计数。
3. 最长不重复连续子串：窗口是否合法？→ Sliding Window。
4. 最短覆盖字符串：满足需求后收缩？→ Sliding Window。
5. 含负数、连续子数组和 K：窗口是否单调？否 → Prefix Sum + Hash。
6. 固定长度窗口最大值：过期元素和当前最大值如何维护？→ Deque。
7. 有序数组两数和：左右移动是否可证明排除？→ Two Pointers。
8. 旋转有序数组查值：哪半有序？→ Binary Search。
9. 最小可行速度/容量：`check(x)` 是否单调？→ Answer Binary Search。
10. 链表反转：前缀已反转、后继未丢？→ Pointer。
11. 链表中点/环：两个速度是否能表达几何关系？→ Fast/Slow。
12. 下一个更大元素：已经等待的元素如何结算？→ Monotonic Stack。
13. 括号嵌套匹配：最近未闭合结构？→ Stack。
14. 第 K 大：是否需要完整顺序？→ Heap/Quickselect。
15. 合并 K 个有序流：当前每条流的最小头？→ Heap。
16. 二叉树层序：按距离层扩展？→ BFS。
17. 无权图最短路：第一次到达是否就是最短？→ BFS。
18. 网格连通块：遍历每个连通区域？→ DFS/BFS。
19. 生成所有子集：选择/不选决策树？→ Backtracking。
20. 排列：每层选择未使用元素？→ Backtracking。
21. 课程依赖：入度能否归零？→ Topological Sort。
22. 动态连通合并：集合代表能否复用？→ Union-Find。
23. 相邻房屋不能同时选：前缀最优是否有重叠子问题？→ DP。
24. 硬币最少：最后一步枚举哪枚硬币？→ DP。
25. 跳跃最远：当前 reach 能否覆盖下一位置？→ Greedy。
26. 区间合并：按 start 排序后相邻重叠？→ Sort + Sweep。
27. 保留最多不重叠区间：结束早是否给未来更多空间？→ Greedy。
28. 股票买卖：状态是持有/不持有还是交易次数？→ DP。
29. 最大子数组和：以当前位置结尾的最优是什么？→ DP/前缀最小值。
30. 矩阵旋转：是否能分层或转置再反转？→ Array transform。
31. 原地去重：读指针和写指针分离？→ Two Pointers。
32. 日历冲突：边界是否需要事件排序？→ Sweep Line。
33. 区间 XOR/和多次查询：是否有可逆前缀？→ Prefix。
34. 字符频次最小覆盖：状态能否增量加入删除？→ Window + Hash。
35. 限定值域的排序：range 是否远小于 n log n？→ Counting/Bucket。
36. 动态 Top-K：每次只保留 k 个候选？→ Heap。
37. 迷宫最少步数：边权都为 1 吗？→ BFS；否则重新判断。
38. 选择方案且必须撤销：状态树是否需要枚举？→ Backtracking。
39. “至少达到某容量是否可行”：predicate 是否单调？→ Binary Search on Answer。
40. “看起来像窗口但有负数”：先检查单调性，可能 → Prefix Sum + Hash。

## 18.2 识别不是关键词匹配

“子串”常提示窗口，但如果约束不是可恢复的合法性，窗口可能不成立；“有序”常提示二分，但仍需定义单调 predicate；“最少”常提示 BFS/DP/贪心，却必须看边权、重复子问题和交换性质。

---

# 19. Unknown Problem Reasoning

下面 20 题先只看问题和提示，算法名称放在答案部分。

## 19.1 题目与提示

1. **服务日志中，找最长连续时间段使错误数不超过 k。**
   - 提示：连续？窗口状态是否能加入/删除？
2. **数组允许负数，求和为 k 的连续段数量。**
   - 提示：右扩是否单调？前缀差如何表达？
3. **一百万个 ID 中找出现次数最多的十个。**
   - 提示：需要完整排序吗？
4. **两个排好序的事件流，输出时间顺序。**
   - 提示：两个当前头哪个更小？
5. **API 延迟数组中找第一个超过阈值的位置，但数据有序。**
   - 提示：找第一个 true 还是遍历？
6. **工单依赖图中判断是否存在循环。**
   - 提示：有向图、入度或三色 DFS。
7. **客服知识段落中找与 query 词重叠最高的前 k 段。**
   - 提示：评分、Top-K、堆；不要自动引入向量库。
8. **链表代表的数字加一。**
   - 提示：进位从尾部来，能否反转或递归？
9. **网格中的客服区域，找最短跨区域步数。**
   - 提示：边权是否一致？多源还是单源？
10. **每个任务有结束时间，最多安排多少不重叠任务。**
    - 提示：哪个局部选择给未来最大空间？
11. **生成所有不重复的标签组合。**
    - 提示：排序、跳过同层重复、决策树。
12. **固定预算下最大化收益，每件资源只能选一次。**
    - 提示：状态是容量前缀，遍历方向如何防止重复？
13. **实时读取数字，随时查询中位数。**
    - 提示：两个堆保持什么关系？
14. **判断一棵树是否符合全局搜索树范围。**
    - 提示：仅比较父子够不够？
15. **查询很多区间的累计请求数。**
    - 提示：前缀预处理是否能换查询时间？
16. **两个字符串能否通过删除字符变成相同。**
    - 提示：双指针还是 DP，取决于允许的操作。
17. **缓存淘汰：每次访问后删除最久未使用项。**
    - 提示：需要 O(1) 查找和 O(1) 调整顺序，两个结构协作。
18. **城市之间逐步加道路，实时判断是否连通。**
    - 提示：集合合并而不是每次 DFS。
19. **机器速度至少多大才能在 deadline 前完成所有工作。**
    - 提示：速度越大越可行吗？
20. **数组中最长严格递增子序列。**
    - 提示：连续吗？如果不连续，窗口不能直接用；考虑状态。

## 19.2 自己推导后的答案索引

1 窗口；2 前缀和+Hash；3 计数+大小 k 的堆；4 双指针/堆；5 二分；6 DFS/拓扑；7 词法评分+堆；8 链表/递归；9 BFS；10 按结束时间贪心；11 回溯；12 0/1 DP；13 双堆；14 上下界递归/中序；15 前缀和；16 双指针或 LCS，视题目操作；17 HashMap+双向链表；18 Union-Find；19 答案二分；20 DP 或 tails 二分。答案不重要，关键是能说出为什么其他模型不成立。

---

# 20. Counterexample Training

| 模型 | 看似可以用 | 反例 | 结论 |
|---|---|---|---|
| Sliding Window | 和超过 K 就左移 | 数组含负数，右扩可能减小和 | 先证明单调性 |
| Greedy | 每次选当前最大硬币 | 硬币 `[1,3,4]` 组成 6，贪心 4+1+1 非最优 | 需要交换/最优性质 |
| Binary Search | 任意数组二分 | predicate true/false 交替 | 必须有单调性 |
| BFS | 所有最短路 | 边权不同，先到不等于代价最小 | 用加权最短路模型 |
| DFS | 找最少步数 | DFS 可能先走很长路径 | 无权最短路用 BFS |
| Hash | 所有“查找” | 需要有序范围查询/排名 | 可能要 TreeMap/排序 |
| Two Pointer | 两个边界 | 无序且移动没有可排除依据 | 先排序或换模型 |
| Monotonic Stack | 找所有右侧关系 | 关系不是“第一个更大/更小”且需要复杂区间 | 重新定义栈不变量 |
| DP | 任何最优化 | 状态无法包含未来所需信息 | 重新设计 state/换搜索 |
| Union-Find | 所有图问题 | 需要最短路径或边顺序 | 并查集只表达连通合并 |
| Heap Top-K | 需要完整有序序列 | k 接近 n 或需要稳定顺序 | 排序可能更简单 |

---

# 21. Java Coding Habits

## 21.1 常用 API 最小集合

```java
Map<Integer, Integer> map = new HashMap<>();
Set<Integer> set = new HashSet<>();
List<Integer> list = new ArrayList<>();
Deque<Integer> deque = new ArrayDeque<>();
PriorityQueue<Integer> minHeap = new PriorityQueue<>();
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
```

常用方法：`getOrDefault`、`merge`、`computeIfAbsent`、`contains`、`offer`、`poll`、`peek`、`push`/`pop`（Deque）、`Arrays.sort`、`StringBuilder.append`。

## 21.2 为什么 ArrayDeque 通常优于 Stack？

`Stack` 是旧的同步类；算法题只需要栈/队列语义，`ArrayDeque` 更直接，也能同时承担 deque。不要把“同步”误认为算法正确性。

## 21.3 int 还是 long？

元素和、乘积、前缀和、面积、时间总量可能超过 int；从约束估算上界，不能等溢出后再修。

## 21.4 Comparator

避免 `a - b` 可能溢出；使用 `Integer.compare(a, b)`、`Comparator.comparingInt` 或明确反向比较。

## 21.5 面试代码习惯

- 变量名表达不变量：`left`、`right`、`prefix`、`need`、`owner`。
- 先写边界和空输入，再写主循环。
- 不为了炫技使用复杂 Stream；可读循环更容易解释和 debug。
- 写完用空、单元素、重复、最大/最小值、溢出和无答案例子手算。

---

# 22. Hot100 Learning Map

以下按模型组织，只给题号、名称和短问题概述，不复制完整题面。核心母题目标 L4，其余根据个人时间达到 L3。

## 22.1 Hash / Array / String

| 题号 | 题名 | 主模型 | 等级 |
|---:|---|---|---|
| 1 | Two Sum | Hash | L4 母题 |
| 49 | Group Anagrams | Hash/计数 | L3 |
| 128 | Longest Consecutive Sequence | HashSet 起点 | L3 |
| 238 | Product of Array Except Self | 前缀/后缀 | L3 |
| 347 | Top K Frequent Elements | Hash + Heap/Bucket | L3 |
| 380 | Insert Delete GetRandom O(1) | Array + Hash | L2 |
| 560 | Subarray Sum Equals K | Prefix + Hash | L4 |
| 41 | First Missing Positive | 原地标记 | L2 |
| 76 | Minimum Window Substring | Window + Hash | L4 |
| 438 | Find All Anagrams | 固定窗口 | L3 |

## 22.2 Two Pointers / Sliding Window

| 题号 | 题名 | 主模型 | 等级 |
|---:|---|---|---|
| 11 | Container With Most Water | 双指针 | L4 母题 |
| 15 | 3Sum | 排序 + 双指针 | L4 |
| 42 | Trapping Rain Water | 双指针/单调栈 | L3 |
| 3 | Longest Substring Without Repeating | 可变窗口 | L4 母题 |
| 424 | Longest Repeating Character Replacement | Window + 最大频次 | L3 |
| 209 | Minimum Size Subarray Sum | 正数窗口 | L3 |
| 283 | Move Zeroes | 快慢指针 | L3 |
| 26 | Remove Duplicates from Sorted Array | 快慢指针 | L3 |
| 125 | Valid Palindrome | 两端指针 | L2 |

## 22.3 Binary Search

| 题号 | 题名 | 主模型 | 等级 |
|---:|---|---|---|
| 704 | Binary Search | 边界不变量 | L4 母题 |
| 33 | Search in Rotated Sorted Array | 分半有序 | L4 |
| 153 | Find Minimum in Rotated Sorted Array | 单调边界 | L3 |
| 34 | Find First and Last Position | lower/upper bound | L3 |
| 875 | Koko Eating Bananas | 答案二分 | L3 |
| 1011 | Capacity To Ship Packages | 答案二分 | L3 |
| 4 | Median of Two Sorted Arrays | 分割/二分 | L2 |

## 22.4 Linked List

| 题号 | 题名 | 主模型 | 等级 |
|---:|---|---|---|
| 206 | Reverse Linked List | 指针反转 | L4 母题 |
| 21 | Merge Two Sorted Lists | 哨兵/双指针 | L3 |
| 141 | Linked List Cycle | 快慢指针 | L3 |
| 142 | Linked List Cycle II | 相遇关系 | L3 |
| 19 | Remove Nth Node From End | 快慢间距 | L3 |
| 23 | Merge k Sorted Lists | Heap/分治 | L3 |
| 160 | Intersection of Two Linked Lists | 双指针换头 | L3 |
| 146 | LRU Cache | Hash + 双链表 | L4 后端思维 |

## 22.5 Stack / Monotonic Stack / Heap

| 题号 | 题名 | 主模型 | 等级 |
|---:|---|---|---|
| 20 | Valid Parentheses | Stack | L3 |
| 155 | Min Stack | 辅助状态 | L3 |
| 739 | Daily Temperatures | 单调栈 | L4 |
| 84 | Largest Rectangle in Histogram | 单调栈 | L3 |
| 215 | Kth Largest Element | Heap/Quickselect | L3 |
| 295 | Find Median from Data Stream | 双堆 | L3 |
| 239 | Sliding Window Maximum | 单调队列 | L3 |

## 22.6 Tree / DFS / BFS

| 题号 | 题名 | 主模型 | 等级 |
|---:|---|---|---|
| 104 | Maximum Depth of Binary Tree | 递归 | L3 |
| 102 | Binary Tree Level Order Traversal | BFS | L4 母题 |
| 226 | Invert Binary Tree | 递归/队列 | L2 |
| 543 | Diameter of Binary Tree | Tree DP | L3 |
| 98 | Validate Binary Search Tree | 边界/中序 | L3 |
| 236 | Lowest Common Ancestor | 递归返回值 | L3 |
| 105 | Construct Binary Tree | 分治/Hash | L2 |
| 199 | Binary Tree Right Side View | BFS/DFS | L2 |
| 200 | Number of Islands | 网格 DFS/BFS | L4 |
| 994 | Rotting Oranges | 多源 BFS | L3 |
| 127 | Word Ladder | BFS | L2 |

## 22.7 Backtracking / Graph / Union-Find

| 题号 | 题名 | 主模型 | 等级 |
|---:|---|---|---|
| 78 | Subsets | 回溯 | L4 母题 |
| 46 | Permutations | 回溯 | L3 |
| 39 | Combination Sum | 回溯/剪枝 | L3 |
| 22 | Generate Parentheses | 回溯/约束 | L3 |
| 51 | N-Queens | 回溯/集合 | L2 |
| 17 | Letter Combinations | 回溯 | L2 |
| 207 | Course Schedule | 拓扑/环检测 | L3 |
| 210 | Course Schedule II | 拓扑排序 | L3 |
| 684 | Redundant Connection | Union-Find | L3 |
| 547 | Number of Provinces | Union-Find/DFS | L3 |

## 22.8 Greedy / Intervals

| 题号 | 题名 | 主模型 | 等级 |
|---:|---|---|---|
| 55 | Jump Game | Greedy reach | L4 |
| 45 | Jump Game II | 区间层级贪心 | L3 |
| 121 | Best Time to Buy and Sell Stock | 前缀最小值 | L3 |
| 122 | Best Time to Buy and Sell Stock II | 局部收益 | L3 |
| 134 | Gas Station | 前缀/贪心 | L3 |
| 763 | Partition Labels | 最远边界 | L3 |
| 56 | Merge Intervals | 排序/扫描 | L4 |
| 57 | Insert Interval | 分段扫描 | L3 |
| 435 | Non-overlapping Intervals | 按结束时间贪心 | L3 |
| 452 | Minimum Number of Arrows | 区间贪心 | L2 |

## 22.9 Dynamic Programming

| 题号 | 题名 | 主模型 | 等级 |
|---:|---|---|---|
| 70 | Climbing Stairs | 一维 DP | L4 母题 |
| 198 | House Robber | 选择/不选 | L4 |
| 322 | Coin Change | 完全背包/最短 | L3 |
| 300 | Longest Increasing Subsequence | 状态/二分 | L3 |
| 1143 | Longest Common Subsequence | 二维 DP | L3 |
| 139 | Word Break | 前缀 DP | L3 |
| 152 | Maximum Product Subarray | 最大/最小状态 | L3 |
| 53 | Maximum Subarray | DP/前缀最小值 | L3 |
| 72 | Edit Distance | 二维状态 | L2 |
| 416 | Partition Equal Subset Sum | 0/1 背包 | L3 |
| 494 | Target Sum | 背包/计数 | L2 |
| 312 | Burst Balloons | 区间 DP | L2 |

这张地图不是要求全部一次做完，而是让你按模型建立迁移关系。每个模型至少选一个母题达到 L4，再用两三个相似题验证不是死记。

---

# 23. Spaced Review Plan

## 23.1 单题复习记录

```text
Problem:
首次模型判断：
暴力瓶颈：
关键不变量：
我第一次的错误：

Day 0：看推导并手写
Day 2：不看答案重做
Day 7：改变一个条件
Day 14：口述复杂度和反例
```

## 23.2 复习什么，而不是重复什么

- Day 0：记住状态和实现细节。
- Day 2：重建暴力到优化的推导。
- Day 7：只看题目，判断模型和失败条件。
- Day 14：迁移到后端业务，例如幂等、日志、库存、依赖图、Top-K。

---

# 24. Mock Coding Interview

## 24.1 固定流程

```text
1. 重述问题
2. 确认输入边界
3. 给暴力方案
4. 计算暴力复杂度
5. 指出重复工作
6. 提出候选模型和成立条件
7. 写不变量
8. 写 Java
9. 用边界例子手算
10. 讨论变形和失败条件
```

## 24.2 八组模拟题

1. Two Sum → 改为返回所有不重复组合。
2. Longest Substring → 改为最多包含 K 种字符。
3. Subarray Sum K → 明确允许负数，解释为什么不用普通窗口。
4. Rotated Array → 改为有重复值。
5. Reverse List → 改为反转区间 `[left,right]`。
6. Level Order → 改为锯齿遍历或右视图。
7. Merge Intervals → 改为支持在线插入。
8. House Robber → 改为环形房屋或必须偷恰好 k 间。

每组至少说出：原模型、改变的状态、复杂度变化、一个反例。

## 24.3 卡住时的表达

> 我先给一个可以证明正确的暴力方案，再定位重复扫描的位置。现在看起来数据是连续/有序/有图结构，我会检查滑动窗口的单调性、二分 predicate 或 BFS 的层级条件，而不是直接套模板。

---

# 25. Final Self Test

## 25.1 100 个推理型问题

1. Hash 解决的核心不是“字符串”，而是什么？
2. Two Sum 的 map 为什么只放过去的元素？
3. Group Anagrams 的 key 必须满足什么等价性？
4. Longest Consecutive 为什么只从没有前驱的数开始？
5. Subarray Sum K 含负数时窗口为什么失效？
6. Prefix Sum + Hash 保存的是前缀值还是区间值？
7. 两指针移动依据必须能证明排除什么？
8. Container With Most Water 为什么移动短板？
9. 3Sum 为什么要排序和跳过重复？
10. Sliding Window 的连续性条件是什么？
11. 可变窗口何时移动 left？
12. 固定窗口与可变窗口的状态更新区别？
13. 每个元素最多被 left/right 处理几次？
14. 负数为什么破坏很多 sum window？
15. Binary Search 真正需要有序数组吗？
16. 如何定义一个单调 predicate？
17. `left + (right-left)/2` 为什么更安全？
18. 旋转数组哪半一定有序？重复值会怎样？
19. 答案二分的搜索空间是什么？
20. 链表反转前为什么保存 next？
21. 快慢指针相遇为什么能判断有环？
22. 两个链表换头为什么能抵消长度差？
23. LRU 为什么需要 HashMap + 双链表？
24. 栈适合表达哪种“最近未完成”关系？
25. 单调栈弹出的元素为什么已经确定答案？
26. 单调队列为什么删除过期索引？
27. PriorityQueue 默认是大根还是小根？
28. Top-K 何时用小根堆？
29. 合并 k 个有序链表的堆大小是多少？
30. Tree recursion 的四问是什么？
31. BST 中序为什么严格递增？
32. 树直径为什么需要返回高度又维护全局答案？
33. BFS 为什么求无权图最短路径？
34. DFS 为什么不保证最短路径？
35. 多源 BFS 的初始队列放什么？
36. 图有环但没有 visited 会怎样？
37. 回溯的 State/Choice/Constraint/Path/Undo 分别是什么？
38. Subsets 和 Permutations 的 index/used 区别？
39. 为什么回溯结果需要复制 path？
40. 剪枝如何证明不漏解？
41. Union-Find 的代表元维护什么？
42. 拓扑排序结束数量不足说明什么？
43. Greedy 必须证明哪种性质？
44. Jump Game 的 reach 不变量是什么？
45. 贪心硬币为什么可能失败？
46. 区间按结束时间排序的交换理由是什么？
47. DP 的 State 不能说清时为什么不能写公式？
48. Climbing Stairs 的空间为何能压缩？
49. House Robber 的 choice 是什么？
50. Coin Change 的 INF 为什么不能用 0 表示？
51. LIS 的 `dp[i]` 与 tails 数组含义有何不同？
52. LCS 的相等/不等转移为什么覆盖所有情况？
53. 0/1 背包容量循环方向影响什么？
54. 区间 DP 为什么状态是 `[l,r]`？
55. O(n) 的“双层 while”如何证明？
56. O(n log k) 的堆来自哪两部分？
57. O(2^n) 是否可能低于输出规模？
58. 递归栈算不算空间？如何在面试中说明？
59. `HashMap` 平均 O(1) 的前提是什么？
60. `ArrayList.remove(0)` 为什么不适合作队列？
61. Comparator 减法为什么可能溢出？
62. 什么时候必须使用 long？
63. 如果数组有负数，先检查哪个条件？
64. 如果边权不同，BFS 是否还适用？
65. 如果 predicate 不单调，二分会丢失什么？
66. 如果必须输出所有方案，复杂度下界是什么？
67. 如果窗口不能通过单边移动恢复合法怎么办？
68. 如果状态需要历史但 dp 只保存一个数怎么办？
69. 如果图节点可重复访问，visited 的时机是什么？
70. 如果区间边界是开区间而不是闭区间，代码如何变化？
71. 如果需要稳定排序，比较器之外还要考虑什么？
72. 如果 k 接近 n，堆和排序怎么选？
73. 如果 Hot100 题换成工单日志，Hash 可以保存什么？
74. 如果状态更新并发，条件更新和双指针有何共同思想？
75. 如果 Idempotency-Key 查询需要 Top-K，哪部分用 Hash/Heap？
76. 如果知识库检索允许负分，能否套滑动窗口？
77. 如果 AI Agent 工具调用有层级依赖，BFS/DFS 如何选择？
78. 如果回复草稿要求最少修改次数，怎样想到 Edit Distance DP？
79. 如果 API 依赖存在环，怎样用拓扑发现？
80. 如果实时读取请求并保持中位数，为什么两个堆？
81. 如果日志时间区间重叠，如何用排序扫描？
82. 如果缓存需要最近最少使用，如何从操作需求推出双链表？
83. 如果题目只问是否存在，不需要保存全部答案吗？
84. 如果数组值域很小，为什么桶排序可能更好？
85. 如果输入在线到达，预排序还可行吗？
86. 如果要恢复 DP 路径，还需要保存什么？
87. 如果回溯有重复输入，去重发生在树的哪一层？
88. 如果 Union-Find 需要删除边，原模型还能直接用吗？
89. 如果树不是二叉树，遍历不变量怎么改？
90. 如果 BFS 内存太大，如何重新评估状态表示？
91. 如果滑动窗口需要计数，删除左端时必须同步什么？
92. 如果题目要求字典序最小，贪心选择是否需要新的证明？
93. 如果排序破坏原下标，怎样保存映射？
94. 如果二分答案的 check 很慢，总复杂度是什么？
95. 如果 Hash key 是可变对象，集合会有什么问题？
96. 如果字符串拼接在循环中，为什么 StringBuilder 更合适？
97. 如果没有看到明显模型，如何从暴力继续推导？
98. 如何用一个反例证明模板不成立？
99. 面试官改变一个约束时，先重算哪个不变量？
100. 你能否在不写代码前说出解法的正确性和复杂度？

## 25.2 自测评分

- 90～100：重点练口述、变形和边界。
- 70～89：模型基本掌握，补失败条件与证明。
- 50～69：停止刷新题，回到母题和暴力推导。
- <50：按 Stage A/B 重新建立数据结构直觉。

---

# 26. Night-Before Coding Interview

## 模型识别表

```text
过去是否出现过？                 Hash
连续且窗口状态可增量维护？         Sliding Window
连续区间和可由前缀差表示？         Prefix Sum
有序/单调 predicate？              Binary Search
两端关系有排除证明？               Two Pointers
最近未匹配/下一个更大？             Stack / Monotonic Stack
只要 Top-K/动态最值？              Heap
按层最短且边权相同？               BFS
递归结构/连通性？                  DFS
选择、约束、撤销、输出所有方案？     Backtracking
局部选择有交换证明？               Greedy
重复子问题 + 最优子结构？           DP
动态连通合并？                     Union-Find
```

## 复杂度

```text
Hash 平均 O(1)
双指针/窗口 O(n)（证明每指针移动次数）
排序 O(n log n)
堆 Top-K O(n log k)
二分 O(log n * check)
BFS/DFS O(V+E)
回溯至少受输出/决策树规模约束
```

## 最后提醒

```text
先说暴力，不要沉默。
先说不变量，不要先背代码。
先证明条件，再选模板。
遇到负数、重复、权重、在线输入，重新检查模型。
```

---

# 27. 与其他学习手册的边界

```text
interview_master_guide.md
    项目事实索引

internship_interview_learning_guide.md
    Java/Spring/Security/MySQL/Redis/AI/Agent 面试主教材

algorithm_reasoning_guide.md
    数据结构、算法模型、Hot100、Java 手写、陌生题推导
```

本文件不修改前两份资料，也不替代项目源码和测试。算法的迁移价值来自推导，而不是声称在 Ticket 代码中已经使用了所有算法。

---

# 28. 学习完成标准

完成本教材后，面对陌生题应能按下面顺序说话：

```text
我先确认输入是否有序、是否连续、是否允许重复和负数。
暴力是……，复杂度是……。
瓶颈在于……被重复计算。
如果我维护……，循环不变量是……。
因此可以使用……，它成立的必要条件是……。
每个元素/状态最多被处理……次，所以复杂度是……。
如果把条件改成……，原模型会失效，因为……，我会改用……。
```

这就是从题目推导答案，而不是从关键词背模板。

---

# 29. 最终审计清单

- [x] 组织方式以模型和母题为主，没有按题号简单堆答案。
- [x] 每个核心模型包含问题原型、Necessary Conditions、INVARIANT、暴力、推导、复杂度和失败条件。
- [x] 核心题使用分级 Hint，Java 实现放在推导之后。
- [x] 包含 Hash、双指针、滑动窗口、前缀和、二分、链表、栈、单调栈、队列、堆、树、DFS、BFS、回溯、图、并查集、贪心、DP、区间和排序。
- [x] 包含 Hot100 按模型重组地图和 L1～L4 目标。
- [x] 包含 40 个模型识别场景、20 个陌生题、反例训练、8 组模拟面试和 100 个自测题。
- [x] 全文只使用 Java 作为主实现语言。
- [x] 没有修改 Java/Python/Vue 代码、测试、配置或既有学习文档。

> 学习资料完成后，下一步不是继续生成更大的资料，而是按 4～6 周路线学习、手写、复述、找反例、模拟面试并投递。
