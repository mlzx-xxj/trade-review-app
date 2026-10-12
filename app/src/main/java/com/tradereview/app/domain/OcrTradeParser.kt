package com.tradereview.app.domain

import com.tradereview.app.data.*
import java.time.LocalDate
import java.text.Normalizer
import java.math.BigDecimal

data class OcrCandidate(val fields:Map<String,String>,val conflicts:Set<String> = emptySet(),val blocked:Boolean=false,
    val closedEvidence:Boolean=false,val problems:List<String> = emptyList())
object OcrTradeParser {
    val labels=linkedMapOf("date" to "交易日期", "market" to "市场", "symbol" to "品种名称", "contractCode" to "合约代码",
        "direction" to "交易方向", "currency" to "币种", "entryPrice" to "入场价", "exitPrice" to "出场价",
        "buyPrice" to "买入价（需核实入出场角色）", "sellPrice" to "卖出价（需核实入出场角色）", "quantity" to "数量／手数", "fees" to "手续费",
        "realizedPnl" to "最终净已实现盈亏")
    val required=setOf("date","market","symbol","direction","currency","realizedPnl")
    private val aliases=mapOf(
        "date" to "交易日期|成交日期|日期|Trade date|Date", "market" to "市场|Market", "symbol" to "品种名称|品种|Symbol|Instrument",
        "contractCode" to "合约代码|合约|Contract", "direction" to "交易方向|方向|Direction", "currency" to "币种|Currency",
        "entryPrice" to "入场成交价|入场价|开仓价|Entry price", "exitPrice" to "出场成交价|出场价|平仓价|Exit price",
        "buyPrice" to "买入价|Buy price", "sellPrice" to "卖出价|Sell price", "quantity" to "成交数量|数量|手数|Quantity|Lots",
        "fees" to "手续费|Fees|Commission", "realizedPnl" to "最终净已实现盈亏|已实现净盈亏|已实现盈亏|Realized net P&L|Realized P&L")
    private val decimals=setOf("entryPrice","exitPrice","buyPrice","sellPrice","quantity","fees","realizedPnl")
    fun parse(raw:String):List<OcrCandidate> {
        require(raw.length<=20_000) { "识别文本过长，请裁剪为清晰的成交记录" }
        val text=Normalizer.normalize(raw,Normalizer.Form.NFKC).replace('−','-').replace('–','-')
        val explicit=text.split(Regex("(?im)^\\s*(?:交易|Trade)\\s*(?:编号|#)?\\s*\\d+\\s*[:：]?\\s*$")).filter { it.isNotBlank() }
        val dated=text.split(Regex("(?im)(?=^\\h*(?:交易日期|成交日期|日期|Trade date|Date)\\h*[:：=])")).filter { it.contains(Regex("(?im)^\\h*(?:交易日期|成交日期|日期|Trade date|Date)\\h*[:：=]")) }
        val blocks=if(explicit.size>1)explicit else if(dated.size>1)dated else text.split(Regex("\\n\\s*\\n")).filter { it.contains(Regex("(?i)已实现|realized|已平仓|closed")) }.takeIf { it.size>1 } ?: listOf(text)
        require(blocks.size<=20) { "一张截图最多辅助处理 20 条，请分开选择" }
        return blocks.map { block ->
            val fields=linkedMapOf<String,String>();val conflicts=mutableSetOf<String>();val problems=mutableListOf<String>()
            for((key,alias) in aliases) {
                val values=Regex("(?im)^\\h*(?:$alias)\\h*[:：=]\\h*([^\\n]+)\\h*$").findAll(block).map { it.groupValues[1].trim() }.toList()
                val valid=values.mapNotNull { normalize(key,it) }.distinct()
                if(valid.size==1 && values.all { normalize(key,it)!=null })fields[key]=valid.single()
                else if(values.isNotEmpty()) { conflicts+=key;problems+="${labels[key]}冲突或格式无效，需人工核对" }
            }
            val blocked=Regex("(?i)委托|持仓|浮动盈亏|未实现|挂单|\\borders?\\b|\\bpositions?\\b|\\bunrealized\\b|K线|candlestick").containsMatchIn(block)
            val closed=Regex("(?i)已平仓|已实现(?:净)?盈亏|\\brealized(?: net)? P&L\\b|\\bclosed trade\\b").containsMatchIn(block)
            if(blocked)problems+="含委托、持仓、浮动或行情信息，只能保存待核实草稿"
            if(!closed)problems+="未发现明确的已平仓或已实现依据，只能保存草稿"
            if("buyPrice" in fields || "sellPrice" in fields)problems+="买卖价格不自动映射入出场；请核对开平仓角色"
            OcrCandidate(fields,conflicts,blocked,closed,problems)
        }
    }
    fun normalize(key:String,value:String):String? = runCatching {
        require(key in labels && value.length<=80 && '\u0000' !in value)
        when(key) {
            "date" -> {
                val m=Regex("(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?").matchEntire(value) ?: error("date")
                LocalDate.of(m.groupValues[1].toInt(),m.groupValues[2].toInt(),m.groupValues[3].toInt()).toString()
            }
            "market" -> mapOf("股票" to "stock","国内期货" to "futures","期货" to "futures","外汇" to "forex","数字资产" to "crypto","stock" to "stock","futures" to "futures","forex" to "forex","crypto" to "crypto")[value.lowercase()] ?: error("market")
            "direction" -> mapOf("做多" to "long","多" to "long","long" to "long","做空" to "short","空" to "short","short" to "short")[value.lowercase()] ?: error("direction")
            "currency" -> mapOf("人民币" to "CNY","CNY" to "CNY","RMB" to "CNY","美元" to "USD","USD" to "USD","USDT" to "USDT")[value.uppercase()] ?: error("currency")
            "contractCode" -> value.also { require(Regex("[A-Za-z0-9_./-]{1,30}").matches(it)) }
            "symbol" -> value.also { require(it.isNotBlank() && it.length<=30) }
            in decimals -> {
                val v=value.replace(Regex("\\s*(元|手|股|USD|CNY|USDT)$",RegexOption.IGNORE_CASE),"")
                require(Regex("[+-]?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,8})?").matches(v))
                val n=BigDecimal(v.replace(",",""));require(n.precision()<=20)
                if(key=="realizedPnl" || key=="fees") { require(n.scale()<=2 && (key=="realizedPnl" || n.signum()>=0));Money.parse(n.toPlainString())?.toPlainString() ?: error("money") }
                else { require(n.signum()>0 && ProfessionalMath.decimal(n.toPlainString())!=null);ProfessionalMath.plain(n) }
            }
            else -> error("field")
        }
    }.getOrNull()
    fun draft(candidate:OcrCandidate,strategy:String,position:Int,key:String=newKey()):EntryDraft {
        val f=candidate.fields;val n=f["realizedPnl"]?.toBigDecimalOrNull()
        return EntryDraft(recordKey=key,date=f["date"]?.let { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() } ?: LocalDate.now().toEpochDay(),
            market=f["market"] ?: "stock",symbol=f["symbol"].orEmpty(),direction=f["direction"].orEmpty(),currency=f["currency"] ?: "CNY",
            amount=n?.takeIf { it.signum()!=0 }?.abs()?.toPlainString().orEmpty(),outcome=n?.signum()?.let { when(it){-1->"loss";0->"flat";else->"gain"} }.orEmpty(),
            strategy=strategy,positionPercent=position,professional=ProfessionalDraft(contractCode=f["contractCode"].orEmpty(),entryPrice=f["entryPrice"].orEmpty(),
                exitPrice=f["exitPrice"].orEmpty(),quantity=f["quantity"].orEmpty(),fees=f["fees"].orEmpty(),quantityUnit=if(f["market"]=="futures")"futures_lot"else "unknown"))
    }
    fun canCreate(c:OcrCandidate,verified:Set<String>,closedConfirmed:Boolean,today:Long=LocalDate.now().toEpochDay()):Boolean {
        if(c.blocked || !c.closedEvidence || !closedConfirmed || !verified.containsAll(required+c.fields.keys))return false
        return required.all { !c.fields[it].isNullOrBlank() } && c.fields.all { normalize(it.key,it.value)==it.value } &&
            runCatching { LocalDate.parse(c.fields.getValue("date")).toEpochDay() in LocalDate.of(1900,1,1).toEpochDay()..today }.getOrDefault(false)
    }
    fun duplicates(c:OcrCandidate,trades:List<Trade>):List<Trade> = trades.filter { t ->
        val f=c.fields
        f["date"]==LocalDate.ofEpochDay(t.date).toString() && f["market"]==t.market && f["symbol"]==t.symbol && f["direction"]==t.direction && f["currency"]==t.currency &&
            f["realizedPnl"]?.toBigDecimalOrNull()?.compareTo(BigDecimal(t.netPnl))==0
    }
}
