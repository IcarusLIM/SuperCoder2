# 题目：网址排雷

给定输入：URL域名过滤规则，URL前缀过滤规则，待匹配的URL。
要求选手编写程序，输出URL的过滤结果。本题主要考察选手如何高效的编写文本匹配，
如何在内存约束下有效的组织数据结构，以及如何有技巧的利用输入的有序性提高性能。

 - [**福利**]  Java程序耗时按85折计算，Python/JS/Perl/Php/Shell等脚本语言耗时按5折计算。
 - [**环境**]  Cpu8核/内存12G。脚本语言优惠到15G，Java/Go优惠到13G。

## 基本规则
用户程序接收4个命令行参数，分别是 域名过滤规则文件 URL前缀过滤规则文件 过滤的URL文件 URL顺序类型。将结果输出到标准输出。
其中URL顺序类型是是用来帮助你更好的优化程序，也可以忽略。

规则文件和URL输入的行分割符都是UNIX格式。

URL顺序类型 = R: 随机顺序; H: url按SCHEME + DOMAINPORT 聚在一起，不保证顺序; S: 全部URL按字典序升序排列。

URL应当先pass域名过滤，然后再pass前缀过滤，产生最终结果。
域名过滤与前缀过滤是"或"的关系。如果一个URL被域名过滤规则或前缀规则的任何一种过滤掉(即匹配下文中的"-"规则)，就视作被过滤掉。

用户输出由如下文本组成

```
    VALUE
    VALUE
    ...
    COUNT_OF_ALLOWED
    COUNT_OF_DISALLOWED
    COUNT_OF_NOHIT
    XOR_OF_ALLOWED_VALUE
    XOR_OF_DISALLOWED_VALUE
```
其中
```
VALUE = 过滤后判定为通过的URL关联的VALUE，不要求顺序 
COUNT_OF_ALLOWED = 过滤后判定为通过的URL条目数
COUNT_OF_DISALLOWED = 过滤后判定为不通过的URL条目数
COUNT_OF_NOHIT = 过滤后没有命中任何规则的条目数，根据题意nohit是allowed的一部分。
XOR_OF_ALLOWED_VALUE = 所有判定为通过的URL关联的VALUE的异或求和
XOR_OF_DISALLOWED_VALUE = 所有判定为不通过的URL关联的VALUE的异或求和
```

## 1. 域名过滤规则文件
域名规则文件每一行一个域名规则，格式如下：
    DOMAIN_FILTER_KEY TAB PERMISSION
例子：
```
abc.com -
abc.cn  +
.abc.com    -
abc.com.cn:8080 +
```

PERMISSION只能是"+"或者"-"，"+"表示通过过滤器(passed)，"-"表示被过滤掉(filtered)。

域名过滤的基本原则是带端口的规则高于不带端口的规则，长的规则高于短的规则。

## 2. URL前缀过滤规则文件
前缀黑名单文件每一行一个域名黑名单，前缀黑名单的格式如下：
    URL_PREFIX_FILTER_KEY TAB RANGE TAB PERMISSION
例子：
```
https://www.abc.com/index.html	=	-
http://www.abc.com/images/	*	+
https://www.abc.com/	+	-
//www.abc.com/index.html?opt=	+	+
```
URL_PREFIX_FILTER_KEY的格式要求见下文。

RANGE只能是"=+*"中的一个。"="表示要求URL精确匹配此条规则，"+"表示要求URL
前缀符合此条规则，且URL长于此前缀，"\*"表示表示要求URL前缀符合此条规则或精确匹配
此规则。

PERMISSION只能是"+"或者"-"，"+"表示通过过滤器，"-"表示被过滤掉。

URL前缀过滤的基本原则是长的规则高于短的规则，端口除了:80和:443的规约以外需要严格
匹配。冲突的规则以"-"优先。

## 3. URL输入文件
每行一个URL和一个关联的长度精确为8的16进制数，且合法的URL长度应小于等于2047字节。

    URL TAB VALUE

例子：
```
http://www.sogou.com/   0A2B3C4D
https://m.sogou.com/search.jsp?q=123   1A2B3C4D
http://www.qq.com:80/1123   00F6A7C8
https://www.qq.com:8443/1123   E5F6A7B0
```

	URL的定义如下：
	URL = SCHEME "://" DOMAIN_PORT PATH
	SCHEME = "http" | "https"
	DOMAIN_PORT = DOMAIN_OR_IP | DOMAIN_OR_IP ":" PORT
	DOMAIN_OR_IP = DOMAIN | IPV4
	DOMAIN = LABEL_DOT_LIST TOP_DOMAIN
	TOP_DOMAIN = LABEL
	LABEL_DOT_LIST = LABEL_DOT LABEL_DOT_LIST | LABLE_DOT
	LABEL_DOT = LABEL "."
	LABEL = [a-zA-Z0-9_\-]+
	PATH = EMPTY | "/" PATHSTRING | "?" PATHSTRING
	PATHSTRING = [\x21-\x7e]+
	EMPTY = 
	
	不符合以上定义的URL视为不合法。
	注意，本题的URL定义并不严格符合URL或者URI对应RFC，请以本题的定义为准。
	我们保证输入文件的的内容都是合法的
	
	输入文件已经对不规范的URL进行了清洗，按照如下规则处理：
	1. 没有路径部分的URL补齐/，例如 https://sina.cn 视作 https://sina.cn/ 。
	2. domainport部分之后，直接以?开始的URL补上/，例如 http://www.qq.cn?123 视作 http://www.qq.cn/?123 。
	3. domain部分如果有大写字母，转化为小写字母。例如 https://SINA.cn/ 视作 https://sina.cn/
	4. scheme部分如果有大写字母，转化为小写字母。例如 HTTPS://sina.cn/ 视作 https://sina.cn/
	5. URL末尾的#以及之后的部分要被截掉。例如 http://www.sogou.com/about.html#copyright 视作 http://www.sogou.com/about.html
	6. 处理后超长的url视作非法url。	

## 4. 规则匹配

### 4.1 域名匹配规则

	DOMAIN_FILTER_KEY 的定义如下：
	DOMAIN_FILTER_KEY = DOMAINPORT | "." DOMAINPORT | TOP_DOMAIN | "." TOP_DOMAIN
	DOMAINPORT 的定义见上文。

取出URL的domain:port部分进行匹配
如果url的scheme是http，且没有指定端口，则端口视作80 
如果url的scheme是https，且没有指定端口，则端口视作443

1. 以"."作为分割点，取包含自身在内的所有的后缀，检查是否与规则匹配。如果有若干匹配，取命中的最长的后缀规则的PERMISSION作为匹配结果。
2. 若1没有结果，则忽略:port部分，重复步骤1。
3. 同样长度的规则有冲突，则"-"优先。
4. 如果没有任何规则命中，则认为匹配结果是通过。

例子：
```
======
基本的例子：
规则：
www.baike.com.cn	+
.baike.com.cn	-
baike.com.cn	+
效果：
http://www.baike.com.cn/ => ALLOWED
https://news.baike.com.cn/ => DISALLOWED
http://news.baike.com.cn/ => DISALLOWED
http://news.baike.com.cn:8080/ => DISALLOWED
https://baike.com.cn/ => ALLOWED
=======
有端口优先于无端口的例子:
规则：
www.798.com.cn	+
.798.com.cn:8080	-
效果：
http://www.798.com.cn/ => ALLOWED
http://www.798.com.cn:8080/ => DISALLOWED
http://news.798.com.cn:8080/ => DISALLOWED
=======
80端口和443端口的特殊情况
80端口的规则默认对以http://开头的URL生效。
443端口的规则默认对以https://开头的URL生效。
例子：
规则：
.mi.com.cn	-
.mi.com.cn:80	+
效果：
http://www.mi.com.cn/ => ALLOWED
https://www.mi.com.cn/ => DISALLOWED
http://www.mi.com.cn:8080/ => DISALLOWED
======
规则有冲突，则"-"优先。
例子：
规则：
www.emacs.com.cn	-
www.emacs.com.cn	+
效果：
http://www.emacs.com.cn/ => DISALLOWED
```

### 4.2 前缀匹配规则

	URL_PREFIX_FILTER_KEY 的定义：
	URL_PREFIX_FILTER_KEY = SCHEME "://" DOMAINPORT PATH | "//" DOMAINPORT PATH
	SCHEME, DOMAINPORT, PATH的定义见上文。

以"//"开头的前缀规则匹配SCHEME部分任意的URL。在本次比赛中，可以视为分解为SCHEME为http和https的两条规则。
"// DOMAINPORT PATH => 
  "http://" DOMAINPORT PATH 
  "https://" DOMAINPORT PATH 


首先将URL处理为正规形式。

1. 如果URL以某一条规则的URL_PREFIX_FILTER_KEY为前缀，且满足该条规则RANGE的要求，则视为匹配。
2. 存在多条匹配的规则时，取URL_PREFIX_FILTER_KEY最长的规则的PERMISSION作为最终匹配结果。
3. 如果多条匹配规则的URL_PREFIX_FILTER_KEY一致，"\*"的优先级低，RANGE为"="或者"+"都优先于"*"。(不存在同时匹配两条URL_PREFIX_FILTER_KEY一致，但是RANGE分别为=和+的情况。)
4. 规则有重复，则"-"优先于"+"。
5. 如果没有任何规则命中，则认为匹配结果是通过。

例子：
```
======
基本的例子：URL_PREFIX_FILTER_KEY的长度优先。
规则：
http://news.gcc.com.cn/about	*	-
http://news.gcc.com.cn/a	+	+
http://news.gcc.com.cn/	*	-
效果：
http://news.gcc.com.cn/about.html => DISALLOWED
http://news.gcc.com.cn/about => DISALLOWED
http://news.gcc.com.cn/abc => ALLOWED
http://news.gcc.com.cn/a => DISALLOWED
http://news.gcc.com.cn/ => DISALLOWED
http://news.gcc.com.cn/copyright.html => DISALLOWED
======
两条规则URL_PREFIX_FILTER_KEY一致，RANGE不一致，"+"比"*"优先。
规则：
http://news.sohu.com.cn/about	*	-
http://news.sohu.com.cn/about	+	+
效果：
http://news.sohu.com.cn/about.html => ALLOWED
http://news.sohu.com.cn/about => DISALLOWED
======
两条规则URL_PREFIX_FILTER_KEY一致，RANGE不一致，"="比"*"优先。
规则：
http://news.163.com.cn/about	*	-
http://news.163.com.cn/about	=	+
效果：
http://news.163.com.cn/about.html => DISALLOWED
http://news.163.com.cn/about => ALLOWED
======
两条规则URL_PREFIX_FILTER_KEY一致，RANGE一致，PERMISSION"-"优先。
规则：
http://news.zi.com.cn/about	*	-
http://news.zi.com.cn/about	*	+
效果：
http://news.zi.com.cn/about.html => DISALLOWED
======
scheme不同视为不匹配，http和https不混淆
规则：
http://news.gl.com.cn/about	*	-
效果：
http://news.gl.com.cn/about.html => DISALLOWED
https://news.gl.com.cn/about.html => ALLOWED
======
端口不同视为不匹配：
规则：
http://news.ruby.com.cn:8080/about	*	-
效果：
http://news.ruby.com.cn/about.html => ALLOWED
https://news.ruby.com.cn:8080/about.html => ALLOWED
======
允许规则或者输入URL强制指定与默认端口不一致的行为，此时匹配原则仍然是scheme和port要一致：
规则：
https://news.java.com.cn/about	*	-
效果：
https://news.java.com.cn:80/about.html => ALLOWED
======
//开头的URL_PREFIX_FILTER_KEY视做http和https两个scheme规则的简写
规则：
//news.perl.com.cn/about	*	-
效果：
http://news.perl.com.cn/about.html => DISALLOWED
https://news.perl.com.cn/about.html => DISALLOWED
规则：
//news.julia.com.cn/about	*	-
https://news.julia.com.cn/a	+	+
效果：
http://news.julia.com.cn/about.html => DISALLOWED
https://news.julia.com.cn/about => DISALLOWED
```

## 5. CASE的数据规模

CASE1:
	用于验证程序正确性。

| Case     | Domain | Prefix\(\+\*\) | Prefix\(=\) | URL              |
|----------|--------|----------------|-------------|------------------|
| Case1\.a | 110万  | 270万          | 75万        | <4000，随机      |
| Case1\.b | 110万  | 270万          | 75万        | 730万，随机      |
| Case1\.c | 110万  | 270万          | 75万        | 730万，HOST分组  |
| Case1\.d | 110万  | 270万          | 75万        | 730万，文本顺序  |

Case2\-4，大数据量

| Case  | domain | Prefix\(\+\*\) | Prefix\(=\) | URL              |
|-------|--------|----------------|-------------|------------------|
| Case2 | 1亿    | 5200万         | 1亿         | 1\.6亿，随机     |
| Case3 | 1\.5亿 | 4400万         | 1亿         | 1\.6亿，HOST分组 |
| Case4 | 1\.5亿 | 5200万         | 1亿         | 1\.6亿，文本顺序 |

CASE5/FINAL:
最终综合CASE。有4个数据集，性质分别对应case1a和/case2/case3/case4，

| Case   | domain | Prefix\(\+\*\) | Prefix\(=\) | URL              |
|--------|--------|----------------|-------------|------------------|
| final1 | 1亿    | 5200万         | 1亿         | <40000，随机     |
| final2 | 1亿    | 5200万         | 1亿         | 3\.6亿，随机     |
| final3 | 1\.5亿 | 4400万         | 1亿         | 4亿，HOST分组    |
| final4 | 1\.5亿 | 5200万         | 1亿         | 5.3亿，文本顺序  |

FinalScore = (final1 * 10 + final2 + final3 + final4) * CPU_DISCOUNT

## 6. 数据分布

    无论domain/prefix，数据都是封禁（-）占绝大多数，不封禁（+）只占少部分，二者数量有大约2个数量级的差距。
    无论domain/prefix，不带端口的规则占绝大多数，带端口的规则只占少部分，二者数量有2个数量级以上的差距。
    在(*+)类型的前缀case中，有相当数量（和规则数目在同一数量级）的前缀规则是封禁到根目录的。
    URL的平均长度在56~64之间（包括http://或者https://）
    所有domain规则，prefix，输入URL，都是合法的且经过了正规化。具体规则见上文。

## 7. 程序运行参数设置

- [Java]
    - 源文件前5行包含 "JavaOpt: -xxx -xxx -xxx ..." 的内容会被引用作为java的启动参数
    - 默认启动参数是 "-Xms5000m -Xmx5000m"
    - 源文件前5行包含 "JavaVer: 11" 可以指定java的版本，目前支持8和11两个版本
    - 默认使用java 11执行
- [python]
    - 支持如下几种执行环境
    - python-2.7  默认
    - python-3.6  若源码第一行包含python3字样
    - pypy(pypy-7.1.1 with python-3.6 beta) 若源码第一行包含pypy3.6字样
    - pypy(pypy-7.0.0 with python-3.5) 若源码第一行包含pypy3.5字样
    - pypy(pypy-5.0.1 with python-2.7) 若源码第一行包含pypy-legacy字样
    - pypy(pypy-7.1.1 with python-2.7) 若源码第一行包含pypy字样
- [C++]
    - 缺省编译参数参数："-std=gnu++11 -g -O2 -pthread -march=corei7-avx -Wall -Werror"
    - 在源文件前5行，通过包含 " SCL: devtoolset-7 " 的形式，可以使用devtoolset带来的新版本gcc
    - SCL: devtoolset-4                   | 使用 gcc-5
    - SCL: devtoolset-6                   | 使用 gcc-6
    - SCL: devtoolset-7                   | 使用 gcc-7
    - SCL: devtoolset-8                   | 使用 gcc-8
    - 在源文件前5行，通过包含 " CXXFLAGS: -g -O2 " 的形式，可以覆盖默认的编译选项

## 8. 运行数据解读
    - 从平台下载的运行数据根据subcase包含3部分的内容
    - xxxx-iostat.log     记录运行期间的磁盘io数据
    - xxxx-pidstat.log    记录运行期间的cpu使用率，检查cpu使用是否充分
    - xxxx.perf.data      记录运行期间详细的cpu热点
    - xxxx.perf.data.tar.bz2      配合perf.data使用
    
    xxxx.perf.data 的具体使用方法如下：
```
] mkdir -p .debug && tar xf xxxx.perf.data.tar.bz2 -C .debug/
]
] perf report --symfs=. -i xxxx.perf.data
]
```


​    