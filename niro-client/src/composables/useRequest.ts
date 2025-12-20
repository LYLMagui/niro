import { ref } from 'vue';

interface UseRequestOptions<T> {
  immediate?: boolean;
  initialData?: T;
  onSuccess?: (data: T) => void;
  onError?: (error: any) => void;
}

export function useRequest<T = any>(
  apiFn: (...args: any[]) => Promise<T>,
  options: UseRequestOptions<T> = {}
) {
  const loading = ref(false);
  const data = ref<T | null>(options.initialData || null);
  const error = ref<any>(null);

  const run = async (...args: any[]) => {
    loading.value = true;
    error.value = null;
    try {
      const res = await apiFn(...args);
      data.value = res;
      if (options.onSuccess) {
        options.onSuccess(res);
      }
      return res;
    } catch (err) {
      error.value = err;
      if (options.onError) {
        options.onError(err);
      }
      throw err;
    } finally {
      loading.value = false;
    }
  };

  if (options.immediate) {
    run();
  }

  return { loading, data, error, run };
}
