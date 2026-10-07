package com.github.lechandonga.pluginextensionruntimejava.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Vincent
 * @version 1.0
 */
@SpringBootTest
public class ApplicationTests {

    @Test
    @DisplayName("Spring 上下文无扩展根目录时也能正常启动（扩展运行时可选）")
    public void contentLoad() {
        assertTrue(true);
    }
}
