package com.tradereview.app.v200

import java.time.LocalDate

/** Exchange contract multipliers and available reference costs; no runtime network. */
object FuturesReferenceCatalog {
    const val verifiedDate="2026-10-15"
    private val effective=LocalDate.of(2026,10,14).toEpochDay()
    data class Product(val prefix:String,val name:String,val exchange:String,val multiplier:String?,val margin:String?,val mode:String="unknown",val open:String?=null,val close:String?=null,val today:String?=null,val referenceContract:String?=null)
    val products=listOf(
        Product("cu","铜","SHFE","5","0.11","turnover_rate","0.00005","0.00005","0.00010","cu2701"),
        Product("bc","铜(BC)","INE","5","0.11","turnover_rate","0.00001","0.00001","0.00000","bc2701"),
        Product("al","铝","SHFE","5","0.11","per_lot","3","3","3","al2701"),
        Product("zn","锌","SHFE","5","0.11","per_lot","3","3","0","zn2701"),
        Product("pb","铅","SHFE","5","0.11","turnover_rate","0.00004","0.00004","0.00000","pb2701"),
        Product("ni","镍","SHFE","1","0.12","per_lot","3","3","3","ni2701"),
        Product("sn","锡","SHFE","1","0.14","per_lot","3","3","3","sn2701"),
        Product("ao","氧化铝","SHFE","20","0.11","turnover_rate","0.0001","0.0001","0.0001","ao2701"),
        Product("ad","铸造铝合金","SHFE","10","0.1","turnover_rate","0.00005","0.00005","0.00000","ad2701"),
        Product("au","黄金","SHFE","1000","0.16","per_lot","20","20","0","au2611"),
        Product("ag","白银","SHFE","15","0.22","turnover_rate","0.00005","0.00005","0.00005","ag2701"),
        Product("rb","螺纹钢","SHFE","10","0.07","turnover_rate","0.0001","0.0001","0.0001","rb2701"),
        Product("wr","线材","SHFE","10","0.12","turnover_rate","0.00004","0.00004","0.00000","wr2701"),
        Product("hc","热轧卷板","SHFE","10","0.07","turnover_rate","0.0001","0.0001","0.0001","hc2701"),
        Product("ss","不锈钢","SHFE","5","0.07","per_lot","2","2","0","ss2701"),
        Product("sc","原油","INE","1000","0.18","per_lot","20","20","0","sc2701"),
        Product("lu","低硫燃料油","INE","10","0.18","turnover_rate","0.00001","0.00001","0.00001","lu2701"),
        Product("fu","燃料油","SHFE","10","0.18","turnover_rate","0.00005","0.00005","0.00000","fu2701"),
        Product("bu","石油沥青","SHFE","10","0.14","turnover_rate","0.00005","0.00005","0.00000","bu2701"),
        Product("br","丁二烯橡胶","SHFE","5","0.12","turnover_rate","0.00002","0.00002","0.00002","br2701"),
        Product("ru","天然橡胶","SHFE","10","0.09","per_lot","3","3","0","ru2701"),
        Product("nr","20号胶","INE","10","0.09","turnover_rate","0.00002","0.00002","0.00000","nr2701"),
        Product("sp","纸浆","SHFE","10","0.07","turnover_rate","0.00002","0.00002","0.00000","sp2701"),
        Product("op","胶版印刷纸","SHFE","40","0.07","turnover_rate","0.00002","0.00002","0.00000","op2701"),
        Product("ec","SCFIS欧线","INE","50","0.17","turnover_rate","0.0006","0.0006","0.0012","ec2701"),
        Product("i","铁矿石","DCE","100",null),
        Product("jm","焦煤","DCE","60",null),
        Product("j","焦炭","DCE","100",null),
        Product("eg","乙二醇","DCE","10",null),
        Product("l","塑料","DCE","5",null),
        Product("pp","聚丙烯","DCE","5",null),
        Product("v","PVC","DCE","5",null),
        Product("m","豆粕","DCE","10",null),
        Product("y","豆油","DCE","10",null),
        Product("p","棕榈油","DCE","10",null),
        Product("c","玉米","DCE","10",null),
        Product("fg","玻璃","CZCE","20",null),
        Product("sa","纯碱","CZCE","20",null),
        Product("ma","甲醇","CZCE","10",null),
        Product("ta","PTA","CZCE","5",null),
        Product("if","沪深300股指","CFFEX","300",null),
        Product("ih","上证50股指","CFFEX","300",null),
        Product("ic","中证500股指","CFFEX","200",null),
        Product("im","中证1000股指","CFFEX","200",null),
    )
    fun find(code:String):Product? {
        val prefix=Regex("^[a-zA-Z]+").find(code.trim())?.value?.lowercase() ?: return null
        return products.firstOrNull{it.prefix==prefix}
    }
    fun rule(code:String,day:Long):LedgerContractRule? {
        val p=find(code) ?: return null
        if(p.multiplier==null || day<effective)return null
        return LedgerContractRule("reference_${p.prefix}_20261015",code,p.name,p.multiplier,p.margin,p.mode,p.open,p.close,p.today,"per_execution","0.01",effective,true)
    }
}
