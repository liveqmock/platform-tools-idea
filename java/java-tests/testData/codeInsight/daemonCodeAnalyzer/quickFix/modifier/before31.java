// "Make 'c class initializer' not static" "true"
import java.io.*;

class c {
    void f() {}

    static {
        <caret>f();
    }
}
