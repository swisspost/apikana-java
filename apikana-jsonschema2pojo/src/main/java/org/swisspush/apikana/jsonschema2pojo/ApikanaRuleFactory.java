package org.swisspush.apikana.jsonschema2pojo;

import com.sun.codemodel.JClassContainer;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;
import org.jsonschema2pojo.util.ParcelableHelper;
import org.jsonschema2pojo.util.ReflectionHelper;

public class ApikanaRuleFactory extends RuleFactory {
    @Override
    public Rule<JPackage, JType> getObjectRule() {
        return new ApikanaObjectRule(this, new ParcelableHelper(), new ReflectionHelper(this));
    }

    @Override
    public Rule<JClassContainer, JType> getTypeRule() {
        return new ApikanaTypeRule(this);
    }
}
