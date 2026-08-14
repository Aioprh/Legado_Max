#!/usr/bin/env python3
"""
书源生成进度小弹窗（tkinter）
在后台线程执行 auto_generate，通过 on_progress 回调实时刷新弹窗显示 AI 当前步骤。

用法（与 auto_source_cli 一致的参数）：
    python progress_gui.py gen-search  --search-url "https://site/search?key={{key}}"
    python progress_gui.py gen-book    --book-url "https://site/book/1/"
    python progress_gui.py gen-search  --search-url "..." --cookie "..." --book-url-template "..."
"""
import sys
import os
import json
import threading
import argparse

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from debugger.engine.auto_source import auto_generate


def collect_args(argv: list = None) -> argparse.Namespace:
    """与 auto_source_cli 保持一致的 CLI 参数"""
    p = argparse.ArgumentParser(prog='progress_gui', add_help=False)
    sub = p.add_subparsers(dest='cmd', required=True)

    ps = sub.add_parser('gen-search')
    ps.add_argument('--search-url', required=True)
    ps.add_argument('--keyword', '-k', default='斗破苍穹')
    ps.add_argument('--name')
    ps.add_argument('--cookie')
    ps.add_argument('--book-url-template')
    ps.add_argument('--toc-paging', action='store_true')
    ps.add_argument('--out')

    pb = sub.add_parser('gen-book')
    pb.add_argument('--book-url', required=True)
    pb.add_argument('--name')
    pb.add_argument('--cookie')
    pb.add_argument('--toc-paging', action='store_true')
    pb.add_argument('--out')

    return p.parse_args(argv)


def build_kwargs(args: argparse.Namespace) -> dict:
    if args.cmd == 'gen-search':
        return {
            'build_from_search': True, 'search_url': args.search_url,
            'keyword': args.keyword, 'source_name': args.name or '',
            'cookie': args.cookie, 'book_url_template': args.book_url_template,
            'enable_toc_paging': args.toc_paging, 'validate': True,
        }
    return {
        'build_from_search': False, 'book_url': args.book_url,
        'source_name': args.name or '', 'cookie': args.cookie,
        'enable_toc_paging': args.toc_paging, 'validate': True,
    }


_STEP_LABEL = {
    'search': '① 搜索列表', 'detail': '② 详情/目录', 'content': '③ 正文',
    'validate': '④ 验证', 'done': '✓ 完成', 'fail': '✗ 失败',
}


def run_gui(args: argparse.Namespace) -> int:
    import tkinter as tk
    from tkinter import ttk

    root = tk.Tk()
    root.title('书源生成进度')
    root.attributes('-topmost', True)
    root.geometry('460x300')
    root.resizable(False, False)
    root.configure(bg='#f5f6fa')

    # 顶部当前状态
    status_var = tk.StringVar(value='正在初始化…')
    step_var = tk.StringVar(value='')
    tk.Label(root, textvariable=status_var, bg='#f5f6fa', fg='#1f2937',
             font=('Microsoft YaHei', 11, 'bold'), anchor='w', wraplength=430,
             justify='left').pack(fill='x', padx=16, pady=(14, 2))
    tk.Label(root, textvariable=step_var, bg='#f5f6fa', fg='#6b7280',
             font=('Microsoft YaHei', 9), anchor='w').pack(fill='x', padx=16)

    bar = ttk.Progressbar(root, mode='determinate', maximum=100)
    bar.pack(fill='x', padx=16, pady=8)

    # 日志区
    log = tk.Text(root, height=7, bg='#ffffff', fg='#374151', relief='flat',
                  font=('Consolas', 9), state='disabled', wrap='word')
    log.pack(fill='both', expand=True, padx=16, pady=(4, 8))

    done_btn = tk.Button(root, text='复制书源 JSON', state='disabled',
                         bg='#2563eb', fg='white', relief='flat', padx=12, pady=4)
    done_btn.pack(pady=(0, 10))

    result_holder = {}

    def _log(msg: str):
        log.configure(state='normal')
        log.insert('end', msg + '\n')
        log.see('end')
        log.configure(state='disabled')

    def _update(step: str, msg: str, frac: float):
        # 主线程安全更新（跨线程通过 root.after 调度）
        root.after(0, lambda: _apply(step, msg, frac))

    def _apply(step: str, msg: str, frac: float):
        status_var.set(msg)
        step_var.set(_STEP_LABEL.get(step, step))
        bar['value'] = int(frac * 100)
        if step in ('search', 'detail', 'content'):
            _log(f'[{_STEP_LABEL.get(step, step)}] {msg}')
        if step == 'done':
            _log('✓ 书源生成成功')
            done_btn.configure(state='normal')
        elif step == 'fail':
            _log('✗ ' + msg)

    def _copy():
        if result_holder.get('json'):
            root.clipboard_clear()
            root.clipboard_append(result_holder['json'])
            status_var.set('已复制到剪贴板 ✓')

    done_btn.configure(command=_copy)

    def worker():
        kwargs = build_kwargs(args)
        try:
            report = auto_generate(on_progress=_update, **kwargs)
        except Exception as e:
            root.after(0, lambda: (_log('✗ 异常: %s' % e),
                                   status_var.set('发生异常'),
                                   bar.__setitem__('value', 100)))
            return
        if report.get('bookSource'):
            raw = json.dumps(report['bookSource'], ensure_ascii=False, indent=2)
            result_holder['json'] = raw
            if args.out:
                try:
                    with open(args.out, 'w', encoding='utf-8') as f:
                        f.write(json.dumps([report['bookSource']], ensure_ascii=False, indent=2))
                    _log(f'已写入 {args.out}')
                except Exception as e:
                    _log(f'写入失败: {e}')

    threading.Thread(target=worker, daemon=True).start()
    root.mainloop()
    return 0


def main() -> int:
    args = collect_args()
    try:
        return run_gui(args)
    except Exception as e:
        print(f'弹窗启动失败（需要图形桌面环境）：{e}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())