/*
 * Copyright (C) 2013 JPEXS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jpexs.decompiler.flash.action.parser.script;

import com.jpexs.decompiler.flash.SWF;
import com.jpexs.decompiler.flash.action.Action;
import com.jpexs.decompiler.flash.action.swf4.ActionGetVariable;
import com.jpexs.decompiler.flash.action.swf4.ActionIf;
import com.jpexs.decompiler.flash.action.swf4.ActionJump;
import com.jpexs.decompiler.flash.action.swf4.ActionNot;
import com.jpexs.decompiler.flash.action.swf4.ActionPop;
import com.jpexs.decompiler.flash.action.swf4.ActionPush;
import com.jpexs.decompiler.flash.action.swf4.ActionSetVariable;
import com.jpexs.decompiler.flash.action.swf4.ConstantIndex;
import com.jpexs.decompiler.flash.action.swf4.RegisterNumber;
import com.jpexs.decompiler.flash.action.swf5.ActionCallFunction;
import com.jpexs.decompiler.flash.action.swf5.ActionDefineFunction;
import com.jpexs.decompiler.flash.action.swf5.ActionGetMember;
import com.jpexs.decompiler.flash.action.swf5.ActionNewObject;
import com.jpexs.decompiler.flash.action.swf5.ActionPushDuplicate;
import com.jpexs.decompiler.flash.action.swf5.ActionSetMember;
import com.jpexs.decompiler.flash.action.swf5.ActionStoreRegister;
import com.jpexs.decompiler.flash.action.swf6.ActionStrictEquals;
import com.jpexs.decompiler.flash.action.swf7.ActionExtends;
import com.jpexs.decompiler.flash.action.swf7.ActionImplementsOp;
import com.jpexs.decompiler.flash.action.treemodel.DirectValueTreeItem;
import com.jpexs.decompiler.flash.action.treemodel.FunctionTreeItem;
import com.jpexs.decompiler.flash.action.treemodel.GetMemberTreeItem;
import com.jpexs.decompiler.flash.action.treemodel.GetVariableTreeItem;
import com.jpexs.decompiler.flash.action.treemodel.operations.Inverted;
import com.jpexs.decompiler.flash.ecma.Null;
import com.jpexs.decompiler.flash.graph.AndItem;
import com.jpexs.decompiler.flash.graph.BreakItem;
import com.jpexs.decompiler.flash.graph.CommaExpressionItem;
import com.jpexs.decompiler.flash.graph.ContinueItem;
import com.jpexs.decompiler.flash.graph.DoWhileItem;
import com.jpexs.decompiler.flash.graph.DuplicateItem;
import com.jpexs.decompiler.flash.graph.ForItem;
import com.jpexs.decompiler.flash.graph.GraphSourceItem;
import com.jpexs.decompiler.flash.graph.GraphTargetItem;
import com.jpexs.decompiler.flash.graph.IfItem;
import com.jpexs.decompiler.flash.graph.NotItem;
import com.jpexs.decompiler.flash.graph.OrItem;
import com.jpexs.decompiler.flash.graph.SourceGenerator;
import com.jpexs.decompiler.flash.graph.SwitchItem;
import com.jpexs.decompiler.flash.graph.TernarOpItem;
import com.jpexs.decompiler.flash.graph.WhileItem;
import com.jpexs.decompiler.flash.helpers.Helper;
import com.jpexs.decompiler.flash.helpers.collections.MyEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author JPEXS
 */
public class ActionScriptSourceGenerator implements SourceGenerator {

    @Override
    public List<GraphSourceItem> generate(List<Object> localData, AndItem item) {
        List<GraphSourceItem> ret = new ArrayList<>();
        ret.addAll(generateToActionList(localData, item.leftSide));
        ret.add(new ActionPushDuplicate());
        ret.add(new ActionNot());
        List<Action> andExpr = generateToActionList(localData, item.rightSide);
        andExpr.add(0, new ActionPop());
        int andExprLen = Action.actionsToBytes(andExpr, false, SWF.DEFAULT_VERSION).length;
        ret.add(new ActionIf(andExprLen));
        ret.addAll(andExpr);
        return ret;

    }

    @Override
    public List<GraphSourceItem> generate(List<Object> localData, OrItem item) {
        List<GraphSourceItem> ret = new ArrayList<>();
        ret.addAll(generateToActionList(localData, item.leftSide));
        ret.add(new ActionPushDuplicate());
        List<Action> orExpr = generateToActionList(localData, item.rightSide);
        orExpr.add(0, new ActionPop());
        int orExprLen = Action.actionsToBytes(orExpr, false, SWF.DEFAULT_VERSION).length;
        ret.add(new ActionIf(orExprLen));
        ret.addAll(orExpr);
        return ret;
    }

    public List<Action> toActionList(List<GraphSourceItem> items) {
        List<Action> ret = new ArrayList<>();
        for (GraphSourceItem s : items) {
            if (s instanceof Action) {
                ret.add((Action) s);
            }
        }
        return ret;
    }

    private List<Action> nonempty(List<Action> list) {
        if (list == null) {
            return new ArrayList<>();
        }
        return list;
    }

    private List<GraphSourceItem> generateIf(List<Object> localData, GraphTargetItem expression, List<GraphTargetItem> onTrueCmds, List<GraphTargetItem> onFalseCmds, boolean ternar) {
        List<GraphSourceItem> ret = new ArrayList<>();
        if (expression instanceof Inverted) {
            ret.addAll(((Inverted) expression).invert().toSource(localData, this));
        } else {
            ret.addAll(expression.toSource(localData, this));
            ret.add(new ActionNot());
        }
        List<Action> onTrue = null;
        List<Action> onFalse = null;
        if (ternar) {
            onTrue = toActionList(onTrueCmds.get(0).toSource(localData, this));
        } else {
            onTrue = generateToActionList(localData, onTrueCmds);
        }

        if (onFalseCmds != null && !onFalseCmds.isEmpty()) {
            if (ternar) {
                onFalse = toActionList(onFalseCmds.get(0).toSource(localData, this));
            } else {
                onFalse = generateToActionList(localData, onFalseCmds);
            }
        }
        byte onTrueBytes[] = Action.actionsToBytes(onTrue, false, SWF.DEFAULT_VERSION);
        int onTrueLen = onTrueBytes.length;

        ActionIf ifaif = new ActionIf(0);
        ret.add(ifaif);
        ret.addAll(onTrue);
        ifaif.setJumpOffset(onTrueLen);
        ActionJump ajmp = null;
        if (onFalse != null) {
            if (!((!nonempty(onTrue).isEmpty())
                    && (onTrue.get(onTrue.size() - 1) instanceof ActionJump)
                    && ((((ActionJump) onTrue.get(onTrue.size() - 1)).isContinue)
                    || (((ActionJump) onTrue.get(onTrue.size() - 1)).isBreak)))) {
                ajmp = new ActionJump(0);
                ret.add(ajmp);
                onTrueLen += ajmp.getBytes(SWF.DEFAULT_VERSION).length;
            }
            ifaif.setJumpOffset(onTrueLen);
            byte onFalseBytes[] = Action.actionsToBytes(onFalse, false, SWF.DEFAULT_VERSION);
            int onFalseLen = onFalseBytes.length;
            if (ajmp != null) {
                ajmp.setJumpOffset(onFalseLen);
            }
            ret.addAll(onFalse);
        }
        return ret;
    }

    @Override
    public List<GraphSourceItem> generate(List<Object> localData, IfItem item) {
        return generateIf(localData, item.expression, item.onTrue, item.onFalse, false);
    }

    private void fixLoop(List<Action> code, int breakOffset) {
        fixLoop(code, breakOffset, Integer.MAX_VALUE);
    }

    private void fixLoop(List<Action> code, int breakOffset, int continueOffset) {
        int pos = 0;
        for (Action a : code) {
            pos += a.getBytes(SWF.DEFAULT_VERSION).length;
            if (a instanceof ActionJump) {
                ActionJump aj = (ActionJump) a;
                if (aj.isContinue && (continueOffset != Integer.MAX_VALUE)) {
                    aj.setJumpOffset(-pos + continueOffset);
                    aj.isContinue = false;
                }
                if (aj.isBreak) {
                    aj.setJumpOffset(-pos + breakOffset);
                    aj.isBreak = false;
                }
            }
        }
    }

    @Override
    public List<GraphSourceItem> generate(List<Object> localData, TernarOpItem item) {
        List<GraphTargetItem> onTrue = new ArrayList<>();
        onTrue.add(item.onTrue);
        List<GraphTargetItem> onFalse = new ArrayList<>();
        onFalse.add(item.onFalse);
        return generateIf(localData, item.expression, onTrue, onFalse, true);
    }

    @Override
    public List<GraphSourceItem> generate(List<Object> localData, WhileItem item) {
        List<GraphSourceItem> ret = new ArrayList<>();
        List<Action> whileExpr = new ArrayList<>();

        List<GraphTargetItem> ex = new ArrayList<>(item.expression);
        if (!ex.isEmpty()) {
            GraphTargetItem lastItem = ex.remove(ex.size() - 1);
            whileExpr.addAll(generateToActionList(localData, ex));
            whileExpr.addAll(toActionList(lastItem.toSource(localData, this))); //Want result
        }

        List<Action> whileBody = generateToActionList(localData, item.commands);
        whileExpr.add(new ActionNot());
        ActionIf whileaif = new ActionIf(0);
        whileExpr.add(whileaif);
        ActionJump whileajmp = new ActionJump(0);
        whileBody.add(whileajmp);
        int whileExprLen = Action.actionsToBytes(whileExpr, false, SWF.DEFAULT_VERSION).length;
        int whileBodyLen = Action.actionsToBytes(whileBody, false, SWF.DEFAULT_VERSION).length;
        whileajmp.setJumpOffset(-(whileExprLen
                + whileBodyLen));
        whileaif.setJumpOffset(whileBodyLen);
        ret.addAll(whileExpr);
        fixLoop(whileBody, whileBodyLen, -whileExprLen);
        ret.addAll(whileBody);
        return ret;
    }

    @Override
    public List<GraphSourceItem> generate(List<Object> localData, DoWhileItem item) {
        List<GraphSourceItem> ret = new ArrayList<>();
        List<Action> doExpr = generateToActionList(localData, item.expression);
        List<Action> doBody = generateToActionList(localData, item.commands);

        int doBodyLen = Action.actionsToBytes(doBody, false, SWF.DEFAULT_VERSION).length;
        int doExprLen = Action.actionsToBytes(doExpr, false, SWF.DEFAULT_VERSION).length;

        ret.addAll(doBody);
        ret.addAll(doExpr);
        ActionIf doif = new ActionIf(0);
        ret.add(doif);
        doif.setJumpOffset(-doBodyLen - doExprLen - doif.getBytes(SWF.DEFAULT_VERSION).length);
        fixLoop(doBody, doBodyLen + doExprLen + doif.getBytes(SWF.DEFAULT_VERSION).length, doBodyLen);
        return ret;
    }

    @Override
    public List<GraphSourceItem> generate(List<Object> localData, ForItem item) {
        List<GraphSourceItem> ret = new ArrayList<>();
        List<Action> forExpr = generateToActionList(localData, item.expression);
        List<Action> forBody = generateToActionList(localData, item.commands);
        List<Action> forFinalCommands = generateToActionList(localData, item.finalCommands);

        forExpr.add(new ActionNot());
        ActionIf foraif = new ActionIf(0);
        forExpr.add(foraif);
        ActionJump forajmp = new ActionJump(0);
        int forajmpLen = forajmp.getBytes(SWF.DEFAULT_VERSION).length;
        int forExprLen = Action.actionsToBytes(forExpr, false, SWF.DEFAULT_VERSION).length;
        int forBodyLen = Action.actionsToBytes(forBody, false, SWF.DEFAULT_VERSION).length;
        int forFinalLen = Action.actionsToBytes(forFinalCommands, false, SWF.DEFAULT_VERSION).length;
        forajmp.setJumpOffset(-(forExprLen
                + forBodyLen + forFinalLen + forajmpLen));
        foraif.setJumpOffset(forBodyLen + forFinalLen + forajmpLen);
        ret.addAll(forExpr);
        ret.addAll(forBody);
        ret.addAll(forFinalCommands);
        ret.add(forajmp);
        fixLoop(forBody, forBodyLen + forFinalLen + forajmpLen, forBodyLen);
        return ret;
    }
    private long uniqLast = 0;

    public String uniqId() {
        uniqLast++;
        return "" + uniqLast;
    }

    @Override
    public List<GraphSourceItem> generate(List<Object> localData, SwitchItem item) {
        List<GraphSourceItem> ret = new ArrayList<>();
        HashMap<String, Integer> registerVars = getRegisterVars(localData);
        int exprReg = 0;
        for (int i = 0; i < 256; i++) {
            if (!registerVars.containsValue(i)) {
                registerVars.put("__switch" + uniqId(), i);
                exprReg = i;
                break;
            }
        }

        ret.addAll(toActionList(item.switchedObject.toSource(localData, this)));


        boolean firstCase = true;
        List<List<ActionIf>> caseIfs = new ArrayList<>();
        List<List<Action>> caseCmds = new ArrayList<>();
        List<List<List<Action>>> caseExprsAll = new ArrayList<>();

        loopm:
        for (int m = 0; m < item.caseValues.size(); m++) {
            List<List<Action>> caseExprs = new ArrayList<>();
            List<ActionIf> caseIfsOne = new ArrayList<>();
            int mapping = item.valuesMapping.get(m);
            for (; m < item.caseValues.size(); m++) {
                int newmapping = item.valuesMapping.get(m);
                if (newmapping != mapping) {
                    m--;
                    break;
                }
                List<Action> curCaseExpr = generateToActionList(localData, item.caseValues.get(m));
                caseExprs.add(curCaseExpr);
                if (firstCase) {
                    curCaseExpr.add(0, new ActionStoreRegister(exprReg));
                } else {
                    curCaseExpr.add(0, new ActionPush(new RegisterNumber(exprReg)));
                }
                curCaseExpr.add(new ActionStrictEquals());
                ActionIf aif = new ActionIf(0);
                caseIfsOne.add(aif);
                curCaseExpr.add(aif);
                ret.addAll(curCaseExpr);
                firstCase = false;
            }
            caseExprsAll.add(caseExprs);
            caseIfs.add(caseIfsOne);
            List<Action> caseCmd = generateToActionList(localData, item.caseCommands.get(mapping));
            caseCmds.add(caseCmd);
        }
        ActionJump defJump = new ActionJump(0);
        ret.add(defJump);
        List<Action> defCmd = new ArrayList<>();
        if (!item.defaultCommands.isEmpty()) {
            defCmd = generateToActionList(localData, item.defaultCommands);
        }
        for (List<Action> caseCmd : caseCmds) {
            ret.addAll(caseCmd);
        }
        ret.addAll(defCmd);

        List<List<Integer>> exprLengths = new ArrayList<>();
        for (List<List<Action>> caseExprs : caseExprsAll) {
            List<Integer> lengths = new ArrayList<>();
            for (List<Action> caseExpr : caseExprs) {
                lengths.add(Action.actionsToBytes(caseExpr, false, SWF.DEFAULT_VERSION).length);
            }
            exprLengths.add(lengths);
        }
        List<Integer> caseLengths = new ArrayList<>();
        for (List<Action> caseCmd : caseCmds) {
            caseLengths.add(Action.actionsToBytes(caseCmd, false, SWF.DEFAULT_VERSION).length);
        }
        int defLength = Action.actionsToBytes(defCmd, false, SWF.DEFAULT_VERSION).length;

        for (int i = 0; i < caseIfs.size(); i++) {
            for (int c = 0; c < caseIfs.get(i).size(); c++) {
                int jmpPos = 0;
                for (int j = c + 1; j < caseIfs.get(i).size(); j++) {
                    jmpPos += exprLengths.get(i).get(j);
                }
                for (int k = i + 1; k < caseIfs.size(); k++) {
                    for (int m = 0; m < caseIfs.get(k).size(); m++) {
                        jmpPos += exprLengths.get(k).get(m);
                    }
                }
                jmpPos += defJump.getBytes(SWF.DEFAULT_VERSION).length;
                for (int n = 0; n < i; n++) {
                    jmpPos += caseLengths.get(n);
                }
                caseIfs.get(i).get(c).setJumpOffset(jmpPos);
            }
        }
        int defJmpPos = 0;
        for (int i = 0; i < caseIfs.size(); i++) {
            defJmpPos += caseLengths.get(i);
        }

        defJump.setJumpOffset(defJmpPos);
        List<Action> caseCmdsAll = new ArrayList<>();
        int breakOffset = 0;
        for (int i = 0; i < caseCmds.size(); i++) {
            caseCmdsAll.addAll(caseCmds.get(i));
            breakOffset += caseLengths.get(i);
        }
        breakOffset += defLength;
        fixLoop(caseCmdsAll, breakOffset);
        return ret;
    }

    @Override
    public List<GraphSourceItem> generate(List<Object> localData, NotItem item) {
        if (item.getOriginal() instanceof Inverted) {
            GraphTargetItem norig = ((Inverted) item).invert();
            return norig.toSource(localData, this);
        }
        List<GraphSourceItem> ret = new ArrayList<>();
        ret.addAll(item.getOriginal().toSource(localData, this));
        ret.add(new ActionNot());
        return ret;
    }

    @Override
    public List<GraphSourceItem> generate(List<Object> localData, DuplicateItem item) {
        List<GraphSourceItem> ret = new ArrayList<>();
        ret.add(new ActionPushDuplicate());
        return ret;
    }

    @Override
    public List<GraphSourceItem> generate(List<Object> localData, BreakItem item) {
        List<GraphSourceItem> ret = new ArrayList<>();
        ActionJump abreak = new ActionJump(0);
        abreak.isBreak = true;
        ret.add(abreak);
        return ret;
    }

    @Override
    public List<GraphSourceItem> generate(List<Object> localData, ContinueItem item) {
        List<GraphSourceItem> ret = new ArrayList<>();
        ActionJump acontinue = new ActionJump(0);
        acontinue.isContinue = true;
        ret.add(acontinue);
        return ret;
    }

    private List<Action> generateToActionList(List<Object> localData, List<GraphTargetItem> commands) {
        return toActionList(generate(localData, commands));
    }

    private List<Action> generateToActionList(List<Object> localData, GraphTargetItem command) {
        return toActionList(command.toSource(localData, this));
    }

    @Override
    public List<GraphSourceItem> generate(List<Object> localData, List<GraphTargetItem> commands) {
        List<GraphSourceItem> ret = new ArrayList<>();
        for (GraphTargetItem item : commands) {
            ret.addAll(item.toSourceIgnoreReturnValue(localData, this));
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    public HashMap<String, Integer> getRegisterVars(List<Object> localData) {
        return (HashMap<String, Integer>) localData.get(0);
    }

    public void setRegisterVars(List<Object> localData, HashMap<String, Integer> value) {
        localData.set(0, value);
    }

    public void setInFunction(List<Object> localData, boolean value) {
        localData.set(1, value);
    }

    public boolean isInFunction(List<Object> localData) {
        return (Boolean) localData.get(1);
    }

    public boolean isInMethod(List<Object> localData) {
        return (Boolean) localData.get(2);
    }

    public void setInMethod(List<Object> localData, boolean value) {
        localData.set(2, value);
    }

    public int getForInLevel(List<Object> localData) {
        return (Integer) localData.get(3);
    }

    public void setForInLevel(List<Object> localData, int value) {
        localData.set(3, value);
    }

    public int getTempRegister(List<Object> localData) {
        HashMap<String, Integer> registerVars = getRegisterVars(localData);
        int tmpReg = 0;
        for (int i = 0; i < 256; i++) {
            if (!registerVars.containsValue(i)) {
                tmpReg = i;
                break;
            }
        }
        return tmpReg;
    }

    private String getName(GraphTargetItem item) {
        if (item instanceof DirectValueTreeItem) {
            DirectValueTreeItem dv = (DirectValueTreeItem) item;
            return (String) dv.getResult();
        }
        if (item instanceof GetVariableTreeItem) {
            GetVariableTreeItem gv = (GetVariableTreeItem) item;
            return getName(gv.name);
        }
        if (item instanceof GetMemberTreeItem) {
            GetMemberTreeItem mem = (GetMemberTreeItem) item;
            return getName(mem.memberName);
        }
        return null;
    }

    private List<String> getVarParts(GraphTargetItem item) {
        List<String> ret = new ArrayList<>();
        do {
            if (item instanceof GetMemberTreeItem) {
                GetMemberTreeItem mem = (GetMemberTreeItem) item;
                ret.add(0, getName(mem));
                item = mem.object;
            }
        } while (item instanceof GetMemberTreeItem);
        String f = getName(item);
        if (f != null) {
            ret.add(0, f);
        }
        return ret;
    }

    private int getVarLength(GraphTargetItem item) {
        int len = 1;
        do {
            if (item instanceof GetMemberTreeItem) {
                GetMemberTreeItem mem = (GetMemberTreeItem) item;
                item = mem.object;
                len++;
            }
        } while (item instanceof GetMemberTreeItem);
        return len;
    }

    private GraphTargetItem removeVarLast(GraphTargetItem item, int cnt) {
        item = (GraphTargetItem) Helper.deepCopy(item);

        for (int i = 0; i < cnt; i++) {
            if (item instanceof GetMemberTreeItem) {
                GetMemberTreeItem mem = (GetMemberTreeItem) item;
                item = mem.object;
            }
        }
        return item;
    }

    private GraphTargetItem addGlobalPrefix(GraphTargetItem item) {
        item = (GraphTargetItem) Helper.deepCopy(item);
        GraphTargetItem first = item;
        GetMemberTreeItem mem = null;
        do {
            if (item instanceof GetMemberTreeItem) {
                mem = (GetMemberTreeItem) item;
                item = mem.object;
            }
        } while (item instanceof GetMemberTreeItem);
        if (item instanceof GetVariableTreeItem) {
            GetVariableTreeItem v = (GetVariableTreeItem) item;
            item = new GetMemberTreeItem(null, new GetVariableTreeItem(null, new DirectValueTreeItem(null, 0, "_global", new ArrayList<String>())), v.name);
            if (mem != null) {
                mem.object = item;
            }
        }
        return first;
    }

    private List<Action> typeToActions(List<String> type, List<Action> value) {
        List<Action> ret = new ArrayList<>();
        if (type.isEmpty()) {
            return ret;
        }
        ret.add(pushConst(type.get(0)));
        if (type.size() == 1 && (value != null)) {
            ret.addAll(value);
            ret.add(new ActionSetVariable());
        } else {
            ret.add(new ActionGetVariable());
        }
        for (int i = 1; i < type.size(); i++) {
            ret.add(pushConst(type.get(i)));
            if ((i == type.size() - 1) && (value != null)) {
                ret.addAll(value);
                ret.add(new ActionSetMember());
            } else {
                ret.add(new ActionGetMember());
            }
        }
        return ret;
    }
    private List<String> constantPool;

    public ActionScriptSourceGenerator(List<String> constantPool) {
        this.constantPool = constantPool;
    }

    public List<String> getConstantPool() {
        return constantPool;
    }

    public DirectValueTreeItem pushConstTargetItem(String s) {
        int index = constantPool.indexOf(s);
        if (index == -1) {
            constantPool.add(s);
            index = constantPool.indexOf(s);
        }
        return new DirectValueTreeItem(null, 0, new ConstantIndex(index), constantPool);
    }

    public ActionPush pushConst(String s) {
        int index = constantPool.indexOf(s);
        if (index == -1) {
            constantPool.add(s);
            index = constantPool.indexOf(s);
        }
        return new ActionPush(new ConstantIndex(index));
    }

    public List<GraphSourceItem> generateTraits(List<Object> localData, boolean isInterface, GraphTargetItem name, GraphTargetItem extendsVal, List<GraphTargetItem> implementsStr, GraphTargetItem constructor, List<GraphTargetItem> functions, List<MyEntry<GraphTargetItem, GraphTargetItem>> vars, List<GraphTargetItem> staticFunctions, List<MyEntry<GraphTargetItem, GraphTargetItem>> staticVars) {
        List<String> extendsStr = getVarParts(extendsVal);
        List<GraphSourceItem> ret = new ArrayList<>();
        List<String> nameStr = getVarParts(name);
        for (int i = 0; i < nameStr.size() - 1; i++) {
            List<Action> notBody = new ArrayList<>();
            List<String> globalClassTypeStr = new ArrayList<>();
            globalClassTypeStr.add("_global");
            for (int j = 0; j <= i; j++) {
                globalClassTypeStr.add(nameStr.get(j));
            }

            List<Action> val = new ArrayList<>();
            val.add(new ActionPush((Long) 0L));
            val.add(pushConst("Object"));
            val.add(new ActionNewObject());
            notBody.addAll(typeToActions(globalClassTypeStr, val));
            ret.addAll(typeToActions(globalClassTypeStr, null));
            ret.add(new ActionNot());
            ret.add(new ActionNot());
            ret.add(new ActionIf(Action.actionsToBytes(notBody, false, SWF.DEFAULT_VERSION).length));
            ret.addAll(notBody);
        }
        List<Action> ifbody = new ArrayList<>();
        List<String> globalClassTypeStr = new ArrayList<>();
        globalClassTypeStr.add("_global");
        globalClassTypeStr.addAll(nameStr);


        ParsedSymbol s = null;
        List<Action> constr = new ArrayList<>();



        if (constructor == null) {
            List<Action> val = new ArrayList<>();
            val.add(new ActionDefineFunction("", new ArrayList<String>(), 0, SWF.DEFAULT_VERSION));
            if (!isInterface) {
                val.add(new ActionStoreRegister(1));
            }
            constr.addAll(typeToActions(globalClassTypeStr, val));
        } else {
            constr.addAll(toActionList(((FunctionTreeItem) constructor).toSource(localData, this)));
            constr.add(new ActionStoreRegister(1));
            constr = (typeToActions(globalClassTypeStr, constr));
        }
        if (!isInterface) {
            for (GraphTargetItem f : staticFunctions) {
                FunctionTreeItem fi = (FunctionTreeItem) f;
                ifbody.add(new ActionPush(new RegisterNumber(1/*static*/)));
                ifbody.add(new ActionPush(getName(fi.calculatedFunctionName)));
                ifbody.addAll(toActionList(fi.toSource(localData, this)));
                ifbody.add(new ActionSetMember());
            }
            for (GraphTargetItem f : functions) {
                FunctionTreeItem fi = (FunctionTreeItem) f;
                ifbody.add(new ActionPush(new RegisterNumber(2/*instance*/)));
                ifbody.add(new ActionPush(getName(fi.calculatedFunctionName)));
                ifbody.addAll(toActionList(fi.toSource(localData, this)));
                ifbody.add(new ActionSetMember());
            }
            for (MyEntry<GraphTargetItem, GraphTargetItem> en : staticVars) {
                ifbody.add(new ActionPush(new RegisterNumber(1/*static*/)));
                ifbody.add(new ActionPush(getName(en.key)));
                ifbody.addAll(toActionList(en.value.toSource(localData, this)));
                ifbody.add(new ActionSetMember());
            }
            for (MyEntry<GraphTargetItem, GraphTargetItem> en : vars) {
                ifbody.add(new ActionPush(new RegisterNumber(2/*instance*/)));
                ifbody.add(new ActionPush(getName(en.key)));
                ifbody.addAll(toActionList(en.value.toSource(localData, this)));
                ifbody.add(new ActionSetMember());
            }
        }

        if (!isInterface) {
            ifbody.add(new ActionPush((Long) 1L));
            ifbody.add(new ActionPush(new Null()));
            ifbody.addAll(typeToActions(globalClassTypeStr, null));
            ifbody.add(pushConst("prototype"));
            ifbody.add(new ActionGetMember());
            ifbody.add(new ActionPush((Long) 3L));
            ifbody.add(pushConst("ASSetPropFlags"));
            ifbody.add(new ActionCallFunction());
        }

        if (constr.isEmpty()) {
            List<Action> val = new ArrayList<>();
            val.add(new ActionDefineFunction("", new ArrayList<String>(), 0, SWF.DEFAULT_VERSION));
            if (!isInterface) {
                val.add(new ActionStoreRegister(1));
            }
            constr.addAll(typeToActions(globalClassTypeStr, val));
        }
        if (!extendsStr.isEmpty()) {
            constr.addAll(typeToActions(globalClassTypeStr, null));
            constr.addAll(typeToActions(extendsStr, null));
            constr.add(new ActionExtends());
        }
        if (!isInterface) {
            constr.add(new ActionPush(new RegisterNumber(1)));
            constr.add(pushConst("prototype"));
            constr.add(new ActionGetMember());
            constr.add(new ActionStoreRegister(2));
            constr.add(new ActionPop());
        }

        if (!implementsStr.isEmpty()) {
            for (GraphTargetItem imp : implementsStr) {
                List<String> impList = getVarParts(imp);
                List<String> globImp = new ArrayList<>();
                globImp.add("_global");
                globImp.addAll(impList);
                constr.addAll(typeToActions(globImp, null));
            }
            constr.add(new ActionPush(new Long(implementsStr.size())));
            constr.addAll(typeToActions(globalClassTypeStr, null));
            constr.add(new ActionImplementsOp());
        }
        ifbody.addAll(0, constr);

        ret.addAll(typeToActions(globalClassTypeStr, null));
        ret.add(new ActionNot());
        ret.add(new ActionNot());
        ret.add(new ActionIf(Action.actionsToBytes(ifbody, false, SWF.DEFAULT_VERSION).length));
        ret.addAll(ifbody);
        ret.add(new ActionPop());
        return ret;
    }

    @Override
    public List<GraphSourceItem> generate(List<Object> localData, CommaExpressionItem item) {
        if (item.commands.isEmpty()) {
            return new ArrayList<>();
        }

        //We need to handle commands and last expression separately, otherwise last expression result will be popped
        List<GraphTargetItem> cmds = new ArrayList<>(item.commands);
        GraphTargetItem lastExpr = cmds.remove(cmds.size() - 1);
        List<GraphSourceItem> ret = new ArrayList<>();
        ret.addAll(generate(localData, cmds));
        ret.addAll(lastExpr.toSource(localData, this));
        return ret;
    }
}