package com.calm.core.browser

/**
 * 网页阅读模式（BR-021）：JS 正文提取 + 排版净化。
 * 提取正文时按 DOM 顺序保留段落与配图（图片不丢失），由 Compose 层渲染。
 */
object ReaderModeJs {

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
          // 只剥离影响排版的节点；图片保留（配图不能丢）
          el.querySelectorAll('script,style,nav,header,footer,aside,form,button').forEach(function(n){ n.remove(); });
          var title = (document.querySelector('h1') || {textContent: document.title}).textContent.trim();
          var items = [];
          // 按文档顺序遍历段落与图片
          var nodes = el.querySelectorAll('p, h2, h3, img');
          var addedImgs = {};
          nodes.forEach(function(n){
            if(n.tagName === 'IMG'){
              var src = n.currentSrc || n.src || n.getAttribute('data-src') || '';
              if(src && src.indexOf('http') === 0 && !addedImgs[src]){
                addedImgs[src] = 1;
                items.push({t:'img', v:src});
              }
            } else {
              var txt = n.innerText.trim();
              if(txt.length > 1) items.push({t:'text', v:txt});
            }
          });
          if(items.length === 0 && el.innerText){
            el.innerText.split('\n').forEach(function(t){ if(t.trim().length > 1) items.push({t:'text', v:t.trim()}); });
          }
          return JSON.stringify({ok:true, title:title, items:items.slice(0, 2000)});
        })();
    """

    sealed class Item {
        data class Text(val value: String) : Item()
        data class Image(val url: String) : Item()
    }

    data class Result(val ok: Boolean, val title: String, val items: List<Item>) {
        companion object {
            fun fromJson(raw: String): Result {
                return try {
                    val json = raw.trim()
                        .removePrefix("\"").removeSuffix("\"")
                        .replace("\\\"", "\"").replace("\\\\", "\\")
                    val o = org.json.JSONObject(json)
                    if (!o.optBoolean("ok")) Result(false, "", emptyList())
                    else {
                        val arr = o.optJSONArray("items") ?: org.json.JSONArray()
                        val items = mutableListOf<Item>()
                        for (i in 0 until arr.length()) {
                            val e = arr.getJSONObject(i)
                            if (e.optString("t") == "img") {
                                items.add(Item.Image(e.optString("v")))
                            } else {
                                items.add(Item.Text(e.optString("v")))
                            }
                        }
                        Result(true, o.optString("title"), items)
                    }
                } catch (_: Exception) {
                    Result(false, "", emptyList())
                }
            }
        }
    }
}
