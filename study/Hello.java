import java.util.Arrays;

public class Hello {
    public static void main(String[] args) {
        String name = "wangyuhang";
        System.out.println("Hello, " + name + "!");

        // 1) 数组：填 1 ~ total，逐个打印
        arrayTest(3);

        // 2) initNames() 返回的是 String[]，接收变量也必须写成 String[]
        String[] names = initNames();

        // 3) 直接 println(names) 只会打出 [Ljava.lang.String;@1b6d3586
        //    要内容就 String.join（拼接）或 Arrays.toString（带中括号）
        System.out.println("names = " + String.join("、", names));
        System.out.println("Arrays.toString = " + Arrays.toString(names));

        // 4) getName：无参版 + 有参版（就是上次学的"重载"）
        System.out.println("getName()        = " + getName());
        System.out.println("getName(names,0) = " + getName(names, 0));
        System.out.println("getName(names,9) = " + getName(names, 9));
    }

    public static void arrayTest(int total) {
        int[] array = new int[total];
        for (int i = 0; i < total; i++) {
            array[i] = i + 1;
            System.out.println(array[i]);
        }
        // length 是字段不是方法：array.length（PHP 里是 count($array)）
        System.out.println("一共 " + array.length + " 个元素");
    }

    /**
     * 返回类型必须和 return 的东西一致：写 String[]，不能写 void。
     * 这里用 return 一个新数组，写法比先声明变量再 return 更直接。
     */
    public static String[] initNames() {
        return new String[] {"张三", "李四", "王五"};
    }

    /** 无参版：给自己一个默认名字 */
    public static String getName() {
        return "wangyuhang";
    }

    /** 有参版：按位置取名字，带边界检查，越界不抛异常而是给提示 */
    public static String getName(String[] names, int index) {
        if (names == null || names.length == 0) {
            return "(名字列表为空)";
        }
        if (index < 0 || index >= names.length) {
            return "(下标越界: " + index + ")";
        }
        return names[index];
    }
}
