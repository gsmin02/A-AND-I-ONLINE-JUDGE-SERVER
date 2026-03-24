import sys
import json
import time
import tracemalloc
import math


def normalize_json_value(value):
    if value is None or isinstance(value, (str, int, bool)):
        return value
    if isinstance(value, float):
        return value if math.isfinite(value) else str(value)
    if isinstance(value, dict):
        return {str(key): normalize_json_value(val) for key, val in value.items()}
    if isinstance(value, (list, tuple)):
        return [normalize_json_value(item) for item in value]
    return str(value)

def execute_solution(namespace, args):
    """
    Measure only the user function call window.
    Excludes parse/compile/bootstrap overhead from memory/time measurement.
    """
    tracemalloc.start()
    start_ns = time.perf_counter_ns()
    try:
        result = namespace["solution"](*args)
        error = None
        output = normalize_json_value(result)
    except Exception as e:
        output = None
        error = f"RUNTIME_ERROR: {e}"
    finally:
        elapsed_ms = (time.perf_counter_ns() - start_ns) / 1_000_000.0
        _, peak_bytes = tracemalloc.get_traced_memory()
        tracemalloc.stop()

    return {
        "output": output,
        "error": error,
        "timeMs": elapsed_ms,
        "memoryMb": peak_bytes / (1024 * 1024),
    }

def main():
    try:
        raw = sys.stdin.read()
        payload = json.loads(raw)
    except Exception as e:
        print(json.dumps({"output": None, "error": f"INTERNAL_ERROR: failed to parse input: {e}", "timeMs": 0.0, "memoryMb": 0.0}))
        sys.exit(1)

    code = payload.get("code", "")
    args = payload.get("args", [])

    namespace = {}
    try:
        exec(compile(code, "<solution>", "exec"), namespace)
    except SyntaxError as e:
        print(json.dumps({"output": None, "error": f"COMPILE_ERROR: {e}", "timeMs": 0.0, "memoryMb": 0.0}))
        sys.exit(0)
    except Exception as e:
        print(json.dumps({"output": None, "error": f"RUNTIME_ERROR: {e}", "timeMs": 0.0, "memoryMb": 0.0}))
        sys.exit(0)

    if "solution" not in namespace or not callable(namespace["solution"]):
        print(json.dumps({"output": None, "error": "RUNTIME_ERROR: 'solution' function not defined", "timeMs": 0.0, "memoryMb": 0.0}))
        sys.exit(0)

    print(json.dumps(execute_solution(namespace, args)))

if __name__ == "__main__":
    main()
