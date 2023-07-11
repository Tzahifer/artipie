/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/artipie/LICENSE.txt
 */
package com.artipie.scripting;

import com.artipie.scripting.Script.PrecompiledScript;
import com.artipie.scripting.Script.ScriptType;
import com.artipie.scripting.Script.StandardScript;
import java.util.HashMap;
import java.util.Map;
import javax.script.ScriptException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

/**
 * Tests for Mvel support by {@link Script}.
 *
 * @since 0.30
 * @checkstyle MagicNumberCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class MvelScriptTest {
    @Test
    public void standardScript() throws ScriptException {
        new StandardScript(ScriptType.MVEL, "System.out.println(1)").call();
        new StandardScript(ScriptType.MVEL, "System.out.println(a)").call(Map.of("a", 2));
        MatcherAssert.assertThat(
            new StandardScript(ScriptType.MVEL, "a * 2").call(Map.of("a", 3)).value(),
            new IsEqual<>(6)
        );
        final Map<String, Object> variables = new HashMap<>();
        variables.put("a", 4);
        MatcherAssert.assertThat(
            new StandardScript(ScriptType.MVEL, "a = a * 3").call(variables).variable("a"),
            new IsEqual<>(12)
        );
    }

    @Test
    public void precompiledScript() throws ScriptException {
        new PrecompiledScript(ScriptType.MVEL, "System.out.println(1)").call();
        new PrecompiledScript(ScriptType.MVEL, "System.out.println(a)").call(Map.of("a", 2));
        MatcherAssert.assertThat(
            new PrecompiledScript(ScriptType.MVEL, "a * 2").call(Map.of("a", 3)).value(),
            new IsEqual<>(6)
        );
        final Map<String, Object> variables = new HashMap<>();
        variables.put("a", 4);
        MatcherAssert.assertThat(
            new PrecompiledScript(ScriptType.MVEL, "a = a * 3").call(variables).variable("a"),
            new IsEqual<>(12)
        );
    }
}
