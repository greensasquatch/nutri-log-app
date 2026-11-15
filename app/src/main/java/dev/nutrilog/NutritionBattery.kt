package dev.nutrilog

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

private val green=Color(0xFF45E879)
private val blue=Color(0xFF23BFFF)
private val orange=Color(0xFFFFB13B)
private val red=Color(0xFFF04D55)
private val empty=Color(0xFF293B4B)

@Composable fun NutritionBattery(nutrientType:Nutrient,currentValue:Double,targetValue:Double?,unit:String) {
    val ratio=NutritionScale.ratio(currentValue,targetValue)
    val cells=NutritionScale.filled(ratio)
    // Compose starts at the initial target and respects the system animator duration scale.
    val transition=updateTransition(cells,label="Nutrition battery")
    val levels=(0 until NutritionScale.cells).map { index ->
        transition.animateFloat(transitionSpec={
            val first=minOf(initialState,targetState)
            val span=kotlin.math.abs(targetState-initialState)
            val changed=kotlin.math.abs(NutritionScale.fraction(targetState,index)-NutritionScale.fraction(initialState,index))
            // Sweep changed cells left-to-right on both gains and losses.
            val msPerCell=if(span>0f) 320f/span else 0f
            tween(durationMillis=(changed*msPerCell).roundToInt().coerceIn(0,320),
                delayMillis=if(changed==0f) 0 else ((index-first).coerceAtLeast(0f)*msPerCell).roundToInt().coerceIn(0,320),
                easing=LinearEasing)
        },label="Cell "+index) { boundary -> NutritionScale.fraction(boundary,index) }
    }
    val percent=ratio?.let { BigDecimal.valueOf(it).movePointRight(2).setScale(1,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+"%" }
    val values=display(currentValue)+(if(ratio!=null) " / "+display(requireNotNull(targetValue)) else "")+" "+unit
    val light=MaterialTheme.colorScheme.surface.luminance()>0.5f
    val panel=if(light) Color(0xFF193040) else Color(0xFF101F2C)
    val text=Color(0xFFEEF6F8)
    val over=if(nutrientType==Nutrient.Protein) blue else orange
    val percentColor=when {
        ratio==null -> text
        ratio<=1.0 -> green
        nutrientType!=Nutrient.Protein && ratio>1.15 -> red
        else -> over
    }
    val shape=RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth().background(panel,shape).border(1.dp,Color(0xFF36576B),shape)
        .clearAndSetSemantics { contentDescription=nutrientType.title+", "+values+", "+(percent?.plus(" of goal") ?: "goal unavailable") }
        .padding(horizontal=12.dp,vertical=8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(nutrientType.title,Modifier.weight(1f),color=text,style=MaterialTheme.typography.titleSmall)
            Column(horizontalAlignment=Alignment.End) {
                Text(values,color=text,style=MaterialTheme.typography.bodySmall)
                Text(percent ?: "Goal unavailable",color=percentColor,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(23.dp).background(Color(0xFF09151E),RoundedCornerShape(3.dp))
            .border(1.dp,Color(0xFF628196),RoundedCornerShape(3.dp))) {
            val pitch=size.width/NutritionScale.cells
            val cellWidth=pitch-minOf(3.dp.toPx(),pitch*0.3f)
            val top=3.dp.toPx()
            val height=size.height-2*top
            repeat(NutritionScale.cells) { index ->
                val left=(index+0.5f)*pitch-cellWidth/2
                drawRect(empty,Offset(left,top),Size(cellWidth,height))
                fun band(start:Float,end:Float,color:Color) {
                    val a=NutritionScale.fraction(start,index)
                    val b=minOf(NutritionScale.fraction(end,index),levels[index].value)
                    if(b<=a)return
                    val pos=Offset(left+a*cellWidth,top)
                    val width=(b-a)*cellWidth
                    drawRect(color,pos,Size(width,height))
                    drawRect(Color.White.copy(alpha=0.24f),pos,Size(width,1.dp.toPx()))
                }
                band(0f,15f,green)
                if(nutrientType==Nutrient.Protein) band(15f,20f,blue) else {
                    band(15f,NutritionScale.warningBoundary,orange)
                    band(NutritionScale.warningBoundary,20f,red)
                }
            }
            val x=size.width*NutritionScale.goalPosition
            drawLine(Color.White,Offset(x,0f),Offset(x,size.height),1.dp.toPx())
        }
        Layout(content={
            for(label in listOf("0%","100% GOAL","133%")) Text(label,color=Color(0xFFB9CDD8),style=MaterialTheme.typography.labelSmall)
        },modifier=Modifier.fillMaxWidth().padding(top=2.dp)) { children,constraints ->
            val width=constraints.maxWidth
            val labels=children.map { it.measure(constraints.copy(minWidth=0)) }
            val middle=(width*NutritionScale.goalPosition-labels[1].width/2f).roundToInt()
            val end=width-labels[2].width
            val wrap=middle+labels[1].width>end || middle<labels[0].width
            val line=labels.maxOf { it.height }
            layout(width,if(wrap) line*2 else line) {
                labels[0].place(0,0)
                labels[1].place(middle.coerceIn(0,width-labels[1].width),if(wrap) line else 0)
                labels[2].place(end,0)
            }
        }
    }
}
