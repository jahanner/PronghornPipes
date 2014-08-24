package com.ociweb.jfast.catalog.generator;

import com.ociweb.jfast.field.OperatorMask;
import com.ociweb.jfast.field.TypeMask;

public class FieldGenerator implements ItemGenerator {

    private final String name;
    private final int id;
    private final boolean presence;
    private final int type;
    private final int operator1;
    private final int operator2;
    private final String initial1;
    private final String initial2;
    
    
    public FieldGenerator(String name, int id, boolean presence, int type, int operator, String initial) {
        this.name = name;
        this.id = id;
        this.presence = presence;
        this.type = type;
        this.operator1 = this.operator2 = operator;
        this.initial1 = this.initial2 = initial;
    }

    public FieldGenerator(String name, int id, boolean presence, int type, int operator1, int operator2, String initial1, String initial2) {
        this.name = name;
        this.id = id;
        this.presence = presence;
        this.type = type;
        this.operator1 = operator1; 
        this.operator2 = operator2;
        this.initial1 = initial1;
        this.initial2 = initial2;
    }
    
    public String toString() {
        return appendTo(new StringBuilder()).toString();
    }
    
    @Override
    public StringBuilder appendTo(StringBuilder result) {
        result.append("<").append(TypeMask.xmlTypeName[type]).append(" name=\"").append(name).append("\" id=\"").append(id).append("\" ");
        if (presence) {
            result.append("prsence=\"optional\" ");
        }
        
        if (TypeMask.TextUTF8==type || TypeMask.TextUTF8Optional==type) {
            result.append("charset=\"unicode\" ");
        }
        result.append(">\n");
                
        //stuff inside the element
        if (TypeMask.Decimal==type || TypeMask.DecimalOptional==type) {
            result.append("<exponent>\n");
            addOperation(result, operator1, initial1);
            result.append("</exponent>\n");
            result.append("<mantissa>\n");
            addOperation(result, operator2, initial2);
            result.append("</mantissa>\n");                        
        } else {
            addOperation(result, operator1, initial1);            
        }
        
        result.append("</").append(TypeMask.xmlTypeName[type]).append(">\n");
        return result;
    }

    private void addOperation(StringBuilder result, int operator, String initial) {
        
        result.append("<").append(OperatorMask.xmlOperatorName[operator]);
        
        if (null!=initial) {
            result.append(" value=\"").append(initial).append("\"");
         } 
        result.append("/>");
    }

}
