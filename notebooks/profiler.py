import time
import numpy as np
from functools import wraps
import matplotlib.pyplot as plt
from collections import deque

def profile_function():
    """
    A decorator that measures function execution time over multiple runs
    and provides visualization capabilities.
    
    
    """
    def decorator(func):
        # Store the last 1000 execution times
        func.execution_times = deque(maxlen=1000)
        
        def reset():
        	func.execution_times = deque(maxlen=1000)

        @wraps(func)
        def wrapper(*args, **kwargs):
            start_time = time.perf_counter()
            result = func(*args, **kwargs)
            execution_time = (time.perf_counter() - start_time) * 1000  # Convert to milliseconds
            func.execution_times.append(execution_time)
            return result
            
        def analyze():
            """Analyze and visualize the function's performance metrics"""
            times = np.array(func.execution_times)
            
            # Create figure with two subplots
            fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 5))
            
            # Histogram of execution times
            ax1.hist(times, bins='auto', alpha=0.7, color='skyblue')
            ax1.set_title(f'Distribution of Execution Times\n{func.__name__}()')
            ax1.set_xlabel('Time (ms)')
            ax1.set_ylabel('Frequency')
            
            # Time series plot
            ax2.plot(times, marker='.', linestyle='-', alpha=0.5, color='skyblue')
            ax2.set_title(f'Execution Times Over Runs\n{func.__name__}()')
            ax2.set_xlabel('Run Number')
            ax2.set_ylabel('Time (ms)')
            
            # Print statistics
            print(f"\nPerformance Statistics for {func.__name__}():")
            print(f"Average time: {np.mean(times):.2f} ms")
            print(f"Median time: {np.median(times):.2f} ms")
            print(f"Std dev: {np.std(times):.2f} ms")
            print(f"Min time: {np.min(times):.2f} ms")
            print(f"Max time: {np.max(times):.2f} ms")
            print(f"95th percentile: {np.percentile(times, 95):.2f} ms")
            
            plt.tight_layout()
            plt.show()
            
        # Attach the analyze method to the wrapper
        wrapper.analyze = analyze
        
        wrapper.reset = reset
        
        return wrapper
    return decorator