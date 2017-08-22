package controllers;

import apimodels.SiblingSet;

import play.mvc.Http;
import play.libs.Json;
import play.Logger;
import java.util.*;
import java.io.FileInputStream;
import javax.validation.constraints.*;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaPlayFrameworkCodegen", date = "2017-08-19T20:56:55.877Z")

public class FunctionsApiControllerImp implements FunctionsApiControllerImpInterface {

    static int collatz (int n) {
        int len = 1;
        while (n > 1) {
            if (n % 2 == 0) n /= 2;
            else n = 3*n + 1;
            ++len;
        }
        return len;
    }
    
    static Set<Integer> collatzSiblings (int len) {
        Set<Integer> siblings = new TreeSet<>();
        siblings.add(1);
        for (int i = 2; i <= len; ++i) {
            Set<Integer> si = new TreeSet<>();
            for (Integer n : siblings) {
                si.add(n*2);
                int p = n-1;
                if (p % 2 != 0) {
                    int m = p / 3;
                    if (p % 3 == 0 && m > 1)
                        si.add(m);
                }
            }
            //Logger.debug("len="+i+" "+si);
            siblings = si;
        }
        return siblings;
    }

    public FunctionsApiControllerImp () {
    }
    
    @Override
    public List<Integer> functionsCollatzSiblingsPost(SiblingSet siblingSet)
        throws Exception {
        //Do your magic!!!
        int len = 0;
        for (Integer i : siblingSet.getSiblingSet()) {
            int l = collatz (i);
            if (len == 0)
                len = l;
            else if (len != l)
                Logger.error("Bogus input: expecting length "
                             +len+" but instead got "+l
                             +"; them ncats guys are a bunch of morons!");
        }

        Logger.debug(" ** collatz length = "+len);
        Set<Integer> seq = collatzSiblings (len);
        for (Integer i : siblingSet.getSiblingSet())
            seq.remove(i);
        Logger.debug(" ** siblings = "+seq.size()+" "+Json.toJson(seq));
        
        return Arrays.asList(seq.toArray(new Integer[0]));
    }
}
