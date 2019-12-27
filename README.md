# Super Coder 超级码力

Sogou公司内部第二届超级码力编程比赛，最终成绩case5 2025秒，排名12，Java实现排名1

[问题描述](https://github.com/Ghamster0/SuperCoder2/blob/master/Problem.md)

### 总体思路

1. 对domain规则和prefix规则分别进行hash，使用long数组存储。hash算法采用Murmur3 128位版本，每条规则需要两个long
2. 对规则数组排序
3. 逐条读入url，由长到短取hash值，二分查找规则数组判定匹配结果

考虑到当规则数组较大时，二分查找的耗时将变得不可接受，这里有两个优化思路：  
1. 使用布隆过滤器快速排除无匹配的url，减少二分查找次数  
2. 创建“fastTable”，快速确定初始查找范围

### 规则加载

- 启用8个规则加载线程，以mmap的方式分片读取规则文件
- 8个规则加载线程共享long规则数组，使用AtomicInteger标记下一空闲位置，各线程使用CAS方式获取插入位置
- 此阶段需要初始化布隆过滤器，domain规则和prefix的"="规则hash值直接存入布隆过滤器，prefix的"+"和`"*"`存储最后一个"/"前子串的`hash^规则长度`

### 排序

- 当数组大小到达1亿数量级，排序耗时将非常可观，以domain规则为例，单线程排序大概需要耗时一分钟左右，多线程排序可以控制在15s以内
- 这里参考`java.util.DualPivotQuicksort`和`java.util.ArraysParallelSortHelpers`做了两个long一组的多线程排序实现。`parallelSort`使用了`Fork/Join`框架，该框架是`work-stealing`算法的实现

### 创建快表

哈希后的规则使用两个long存储，理论上数值应当在`Long.MIN_VALUE~Long.MAX_VALUE`间均匀分布的。取前k位，则有：
```
下标： 0       1        ...  a-1     a(a>0)   ...  b-1     b(b>a)   ...  c(c>b)
前k位：10...00 10...00  ...  10...00 10...01  ...  10...01 10...10  ...  10...11
```
遍历规则数组，不难找出每种前缀第一次出现的索引，和最后一次出现的索引，将这些索引记录到一个`int[2][]`的二维数组中，便得到了快表`fastTable`，例如：
```
前缀类型：      10..00 10..01 10..10 10..11  ...
fastTable索引：  0      1      2      3      ...
fastTable[0]:    0      a      b      c      ...
fastTable[1]:    a      b      c      -      ...
```
随后，如果遇到哈希值前缀为`10..10`的url，通过查询`fastTable[0][2]`和`fastTable[1][2]`可知，在规则数组的b到c之间执行二分查找即可

### 规则匹配

1. 对于domain规则，将url的域名部分按"."分割，由长到短匹配；首先查询bloomFilter，当规则可能存在时，查询快表确定二分查找范围，最后进行二分查找
2. prefix规则"="的情况与domain相同
3. 对于prefix规则"+"和`"*"`的情况，分别截取全部长度、去除最后一位……进行匹配，方式与上面相同

### IO优化

Java默认的`InputStream`本身速度并不慢，但`BufferedReader.readLine()`却非常慢。

考虑到本次比赛规则和url只包含英文字符和符号，所以字节流和字符流本身是等价的，可以省去Java`InputStream`转`InputReader`环节

最终使用了`FileChannel.map`的方式进行文件读取，这里需要对得到的`MappedByteBuffer`立即调用`load`方法将映射的分片一次性读入内存，以最大程度确保顺序读取，map的起始位置和长度4K对齐

`System.out.println`有加锁操作，多线程频繁竞争下性能很差。每个匹配线程可以缓存一定量过滤结果之后一次性输出。使用`BufferedOutputStream`封装`System.out`可以带来一定性能提升

### 其他

对于字典序和host有序的url，domain filter可以暂存上次处理的域名、端口和结果，若下次相同则可省去布隆过滤器查找和二分查找
