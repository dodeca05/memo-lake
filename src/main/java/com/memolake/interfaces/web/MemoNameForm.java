package com.memolake.interfaces.web;

import com.memolake.application.NameRules;
import com.memolake.application.ValidName;
import jakarta.validation.constraints.NotBlank;

/** "New memo" form on the topic page: asks for the name, then opens the editor. */
public class MemoNameForm {

    @NotBlank(message = "메모 이름을 입력하세요.")
    @ValidName(message = NameRules.RULE_DESCRIPTION_KO)
    private String memoName;

    public String getMemoName() {
        return memoName;
    }

    public void setMemoName(String memoName) {
        this.memoName = memoName;
    }
}
