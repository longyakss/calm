package com.calm.core.browser

/**
 * 网页阅读模式（BR-021）：JS 正文提取 + 排版净化。
 * 简化版 readability：取 DOM 中文本密度最高的容器，剥离脚本/样式/导航。
 */
object ReaderModeJs {

    /** 注入后调用 window.__calmReaderOut 拿不到返回值，故用 console 或 JS 接口回传；
     *  这里约定：evaluateJavascript 的返回值直接是 JSON 字符串。 */
    const val EXTRACT = """
        (function(){
          function pick(root){
            var cands = root.querySelectorAll('article, .article, .content, #content, .post, #main, main, .entry, .detail, .text, .article-content, #article');
            var best = null, bestLen = 0;
            cands.forEach(function(c){
              var len = c.innerText ? c.innerText.length : 0;
              if(len > bestLen){ best = c; bestLen = len; }
            });
            if(!best || bestLen < 200){
              var divs = document.body ? document.body.querySelectorAll('div') : [];
              divs.forEach(function(d){
                var len = d.innerText ? d.innerText.length : 0;
                var bad = (d.querySelectorAll('a').length / Math.max(1, len)) > 0.05;
                if(len > bestLen && !bad){ best = d; bestLen = len; }
              });
            }
            return best;
          }
          var el = pick(document);
          if(!el){ return JSON.stringify({ok:false}); }
          el.querySelectorAll('script,style,nav,header,footer,aside,iframe,form,button').forEach(function(n){ n.remove(); });
          var title = (document.querySelector('h1') || {textContent: document.title}).textContent.trim();
          var paras = [];
          el.querySelectorAll('p, h2, h3').forEach(function(p){
            var t = p.innerText.trim();
            if(t.length > 1) paras.push(t);
          });
          if(paras.length === 0){
            el.innerText.split('\n').forEach(function(t){ if(t.trim().length > 1) paras.push(t.trim()); });
          }
          return JSON.stringify({ok:true, title:title, paras:paras.slice(0, 2000)});
        })();
    """

    data class Result(val ok: Boolean, val title: String, val paras: List<String>) {
        companion object {
            fun fromJson(raw: String): Result {
                return try {
                    val o = org.json.JSONObject(
                        raw.trim().removePrefix("\"").removeSuffix("\"")
                            .replace("\\\"", "\"").replace("\\\\", "\\"),
                    )
                    if (!o.optBoolean("ok")) Result(false, "", emptyList())
                    else Result(
                        true,
                        o.optString("title"),
                        buildList {
                            val arr = o.optJSONArray("paras")
                            if (arr != null) for (i in 0 until arr.length()) add(arr.getString(i))
                        },
                    )
                } catch (_: Exception) {
                    Result(false, "", emptyList())
                }
            }
        }
    }
}
