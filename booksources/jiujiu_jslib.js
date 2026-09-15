function Config() {
    const { source, java } = this;
    let variable;
    try{
        variable = JSON.parse(source.getVariable());
    } catch(e) {
        variable = {
        	    tab: 0,
        	    mode: "♟️综合",
        	    tone_id: 0,
        	    source: "qidian",
        	    bookComment: false
        	}
        source.setVariable(JSON.stringify(variable));
        java.toast("初始化成功！");
    }
    return variable
}

const api = [
    "https://sunianxin.cmcure.com",
    "https://serene.sunianxincue.love"
]

const version = "2.25";

function getCover(bId) {
	   return `https://qidian.qpic.cn/qdbimg/349573/${bId}/600`
}

const ho = "https://m.qidian.com";

function checkEnv() {
    let { java, source } = this;
    try {
        new Packages.io.legato.kazusa.utils.TimeoutCancellationException('');
        return true;
    } catch (e) {
        return typeof source.loginUi == 'function' ? false : true;
    }
}

function isQRead() {
    let { java } = this;
    try {
        return java.qread && java.qread() == "1";
    } catch (e) {
        return false;
    }
}

function ShowComments(content, sources, book_id, item_id) {
    let { java, source, cache } = this;

    let lines = String(content).split(/\r\n|\n/);

    let contentLineMap = [];
    let lineIndex = 0;
    for (let i = 0; i < lines.length; i++) {
        let text = String(lines[i]).replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim();
        if (text.length > 0) {
            contentLineMap[lineIndex] = i;
            lineIndex++;
        }
    }
    let totalParagraphs = lineIndex;

    let distUrl = `${api[1]}/api/fanqie/comments/${sources}/${book_id}/${item_id}`;
    let distributions = [];
    let bookCommentCount = 0;

    try {
        let resp = JSON.parse(java.ajax(distUrl));
        if (resp.code === 0 && resp.data && Array.isArray(resp.data.distributions)) {
            resp.data.distributions.forEach(d => {
                if (d.para_index == "-1") {
                    bookCommentCount = d.count || 0;
                } else {
                    distributions.push(d);
                }
            });
        }
    } catch (e) {
        java.log("获取段评分布失败: " + e);
        return content;
    }

    let color = "#666666";
    let loginInfo = source.getLoginInfoMap() || {};
    if (loginInfo['段评图标颜色'] && String(loginInfo['段评图标颜色']).startsWith('#')) {
        color = loginInfo['段评图标颜色'];
    }
    let customSvgRaw = loginInfo['自定义气泡SVG'] || "";

    distributions.forEach(d => {
        let paraIndex = parseInt(d.para_index);
        if (isNaN(paraIndex) || paraIndex < 0 || paraIndex >= totalParagraphs) return;
        if (!d.count || d.count <= 0) return;

        let realLineIndex = contentLineMap[paraIndex];
        if (realLineIndex >= 0 && realLineIndex < lines.length) {
            lines[realLineIndex] += createSvg.call(this, d.count, color, book_id, item_id, paraIndex, sources, customSvgRaw);
        }
    });

    if (bookCommentCount > 0) {
        let bookSvg = createSvg.call(this, bookCommentCount, color, book_id, item_id, "-1", sources, customSvgRaw);
        lines.push(bookSvg);
    }

    return lines.join("\n");
}

function createSvg(number, color, bid, cid, para, sourceType, customSvgRaw) {
    let { java } = this;
    let isMod = checkEnv.call(this);
    let isQing = isQRead.call(this);

    let displayText = number > 99 ? "99+" : String(number);
    let date = String(Date.now()).match(/(\d{6}$)/)[1]; // 用于缓存控制

    // 处理自定义 SVG 模板
    let customSvg = "";
    if (customSvgRaw) {
        if (typeof customSvgRaw == 'function') {
            try { customSvg = customSvgRaw(); } catch (e) {}
        } else if (typeof customSvgRaw == 'string') {
            customSvg = customSvgRaw;
        } else {
            customSvg = String(customSvgRaw);
        }
    }

    var svg;
    if (isQing || isMod) {
        // 修改版/轻读版
        if (customSvg && customSvg.startsWith('<svg') && customSvg.endsWith('</svg>')) {
            svg = customSvg.replace(/\{\{color\}\}/g, color).replace(/\{\{displayText\}\}/g, displayText);
        } else {
            svg = '<svg width="160" height="120" xmlns="http://www.w3.org/2000/svg"><path d="M 55 10 L 120 10 Q 150 10 150 40 L 150 80 Q 150 110 120 110 L 55 110 Q 25 110 25 80 L 25 75 L 3 60 L 25 45 L 25 40 Q 25 10 55 10 Z" fill="none" stroke="' + color + '" stroke-width="5" stroke-linejoin="round"/><text x="87" y="77" font-family="Arial, sans-serif" text-anchor="middle" dominant-baseline="middle" font-size="50" font-weight="bold" fill="' + color + '">' + displayText + '</text></svg>';
        }
    } else {
        // 原版
        if (customSvg && customSvg.startsWith('<svg') && customSvg.endsWith('</svg>')) {
            svg = customSvg.replace(/\{\{color\}\}/g, color).replace(/\{\{displayText\}\}/g, displayText);
        } else {
            svg = '<svg width="1000" height="909" xmlns="http://www.w3.org/2000/svg">' +
                  '<path d="M80,80 h840 a60,60 0 0 1 60,60 v580 a60,60 0 0 1 -60,60 h-620 l-140,90 v-90 h-80 a60,60 0 0 1 -60,-60 v-580 a60,60 0 0 1 60,-60 z" ' +
                  'fill="none" stroke="' + color + '" stroke-width="16" stroke-linejoin="round"/>' +
                  '<text x="500" y="450" font-family="Arial, sans-serif" text-anchor="middle" ' +
                  'font-size="360" fill="' + color + '" dy="0.3em">' + displayText + '</text>' +
                  '</svg>';
        }
    }

    var encodedSvg = java.base64Encode(svg);

    let clickFn = 'showCmt("' + bid + '","' + cid + '","' + para + '","' + date + '","' + sourceType + '")';

    // 根据环境选择触发方式
    if (isMod) {
        return '<img src="data:image/svg+xml;base64,' + encodedSvg + ',{\'click\':\'' + clickFn + '\',\'style\':\'TEXT\'}">';
    } else {
        return '<img src="data:image/svg+xml;base64,' + encodedSvg + ',{\'js\':\'' + clickFn + '\',\'style\':\'TEXT\'}">';
    }
}

function showCmt(bid, cid, para, date, source) {
    let { java, cache } = this;

    let url = `${api[1]}/api/fanqie/comments/index.php/ui/${source}/${bid}/${cid}/${para}`;

    // 防抖处理
    if (!checkEnv.call(this)) {
        let mname = `sc-${bid}-${cid}-${para}`;
        let load = (cache.getFromMemory(mname) ?? "-").split("-");
        if (load[0] != "1" || load[1] != date) {
            cache.putMemory(mname, "1-" + date);
            return;
        }
        let lastKey = `lastClick_${bid}_${cid}_${para}`;
        let lastTime = cache.getFromMemory(lastKey) || 0;
        if (Date.now() - lastTime < 1000) return;
        cache.putMemory(lastKey, Date.now());
    }

    // 根据阅读器版本选择打开方式
    if (isQRead.call(this)) {
        java.startBrowserDp(url, "段评");
    } else if (checkEnv.call(this)) {
        // 修改版
        try {
            java.showBrowser(
                url,
                null,
                `window.java=java;`,
                JSON.stringify({
                    "expandedCornersRadius": 20,
                    "dismissOnTouchOutside": true,
                    "isDraggable": true,
                    "shouldDimBackground": true,
                    "backgroundDimAmount": 0.5,
                    "hardwareAccelerated": true,
                    "isNestedScrollingEnabled": true,
                    "isGestureInsetBottomIgnored": true,
                    "setFitToContents": false,
                    "heightPercentage": 0.75,
                    "isHideable": true
                })
            );
        } catch (e) {
            java.startBrowser(url, "段评");
        }
    } else {
        java.startBrowser(url, "段评");
    }
}

function deviceType() {
    let { java } = this;
    try {
        java.deviceID(); 
        return false; 
    } catch (e) {
        try {
            java.androidId(); 
            return true; 
        } catch (e) {
            return false;
        }
    }
}

function getSessionId() {
    const { cookie, source } = this;
    try {
        let cookieHeader = String(cookie.getCookie('fanqienovel.com'));
        let sessionId = cookieHeader.match(/sessionid=([^;]+)/)?.[1] || null;
        
        if (sessionId) {
            return sessionId;
        }
    } catch (e) {
    }
    
    try {
        let loginInfo = source.getLoginInfoMap() || {};
        return loginInfo['手动登录Token'] || "";
    } catch (e) {
        return "";
    }
}
