# HiJson，一款json字符串格式化查看的桌面工具

HiJson一款json字符串格式化查看工具,在web开发中，开发者若使用json作为数据交换格式，那么她将给开发者带来非常强大的的辅助，可谓是web开发必备之神器。

JSON作为一种数据交换格式越来越多地被用于web开发中，并在很多场合中取代了xml。具有相同的数据信息，json有比XML更小的数据传输量，和在前台开发中更方便的操作性。但json字符串的缺点是不方便开发人员直接阅读，对于数据问题的定位带来非常大的不方便。相对于xml多如牛毛的各种解析查看工具，json这方面的工具却是凤毛麟角。HiJson为json字符串的解析查看，带来的非常大的方便性，工具虽小，但功能强大。为开发人员提供不可或缺的辅助作用，web程序员之必备！

HiJson使用java开发，基于gson包，格式化json字符并以树的形式展示，支持各种常用操作，支持多标签！

基于 [HiJson](https://code.google.com/p/json-view/) by [藏言(Cangyan)](mailto:beetle082@163.com) 二次开发。

License: [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)

## Requirement

* JDK 21 or higher
* NetBeans / IntelliJ IDEA, or Apache Ant 1.10.x for command-line builds

## Dependencies

All dependency JARs belong in `lib/`. IntelliJ IDEA loads this directory;
the Ant classpath is listed in `nbproject/project.properties`.


build and run:
```shell
ant jar
java -jar dist/HiJson.jar
```

Keep `dist/lib/` beside `dist/HiJson.jar` when distributing this build.

