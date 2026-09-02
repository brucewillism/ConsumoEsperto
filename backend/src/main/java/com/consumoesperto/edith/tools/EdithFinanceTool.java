package com.consumoesperto.edith.tools;

import java.util.Map;

/**
 * Tool financeira read-only invocável pelo Tool Bridge E.D.I.T.H.
 */
public interface EdithFinanceTool {

    String name();

    Map<String, Object> execute(String contextRef, Map<String, Object> input);
}
